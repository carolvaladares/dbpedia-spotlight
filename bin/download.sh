#!/bin/bash
#+------------------------------------------------------------------------------------------------------------------------------+
#| DBpedia Spotlight - Download script                                                                                          |
#| @author @sandroacoelho @zaknarfen                                                                                                  |
#+------------------------------------------------------------------------------------------------------------------------------+
PROGNAME=$(basename $0)

##### Config parameters (adjust according your target language and folder)

export lang_i18n=pt
export comp_languages=(en bg ca cs de el es fr hu it ko pl ru sl tr)
export language=portuguese
export dbpedia_workspace=/var/local/spotlight
export dbpedia_version=3.8
export comp_types=0
DIRECTORY_PREFIX=$dbpedia_workspace + "/dbpedia_data/original/dbpedia/"
OPENNLP_PREFIX="http://opennlp.sourceforge.net/models-1.5/"

#+------------------------------------------------------------------------------------------------------------------------------+
#| Functions                                                                                                                    |
#+------------------------------------------------------------------------------------------------------------------------------+

# Error_exit function by William Shotts. http://stackoverflow.com/questions/64786/error-handling-in-bash
function error_exit
{
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}
touch $dbpedia_workspace/foo && rm -f $dbpedia_workspace/foo || error_exit "ERROR: The directory '$dbpedia_workspace' is not writable! Change its permissions or choose another 'dbpedia_workspace' in download.sh"

# The function used to create all the directories needed
function create_dir()
{
    if [ -e $1 ]; then
        echo $1 'already exists.'
    else
        mkdir $1
    fi
}

# The function used to test the languages used as complement to the initial language
function test_comp_lang()
{
    local arr=$1
    # Loop through the languages array
    for i in ${arr[@]}
    do
       # Checking if the complement languages supplied are valid
       if [ $(expr length $i) -ne 2 ] | [ "$i" == "$lang" ] | `expr match "$i" '[A-Za-z]'` -ne 2 ]
           error_exit
    done
}

# Just some initial processing for the type complement task
init
{
    # We will need the instance types file in the .nt format so we can create a TDB store using it. For that
    # we unzip the .bz2 file related to the initial language
    bunzip2 $DIRECTORY_PREFIX$lang + /instance_types_$lang_i18n.nt.bz2

    # We also need to download the bijective interlanguage links file of the initial language.
    download_file http://downloads.dbpedia.org/$dbpedia_version/$lang_i18n/interlanguage_links_same_as_$lang_i18n.nt.bz2
}

# Just a helper function to download files from a given path. The first parameter is the path from where to download the file
# and the second states the language, which is useful when dealing with multiple languages
download_file()
{
    # Only downloads if there is no current file or there is a newer version
    case "$#" in {
        "1")
            wget -N $1
            ;;
        "2")
            wget -N $1 --directory-prefix=$DIRECTORY_PREFIX$2
            ;;
        *)
            error_exit; }
    echo 'done!'
}

# A slightly modified version of the download function. This time if we don't find the initial language files
# we change the path to get the english OpenNLP files
dl_opennlp_file()
{
    local file=$1'-'$2".bin"
    local path=$OPENNLP_PREFIX$file
    local new_path=""
    local string_to_replace=""
    wget -q --spider $path
    if [ $? -eq 0 ] ; then
        wget -N $path
    else
        echo "$file not found. Getting an English version..."
        string_to_replace=$1'-'
        new_path=$(echo $path | sed -e s/"$string_to_replace"/en-/g)
        echo $new_path
        wget -q --spider $new_path
        if [ $? -eq 0 ] ; then
            wget -N $new_path
        else
            string_to_replace="en-"
            new_path=$(echo $new_path | sed -e s/"$string_to_replace"/en-ner-/g)
            wget -N $new_path
        fi
    fi
}

# The function used in case the user wants to complement the instance types triples file
function complement_types()
{
    echo "Do you want to complement your language instance types file? the new file will be used in the indexing stage (optional)."
    select yn in "Yes" "No"; do
        case $yn in
            # Test if the complement languages array is empty or not defined
            Yes ) test_comp_lang [ test $comp_languages:-$(error_exit) ]
                  init_complement
                  # Now for each complement language defined above we create the respective directories and download all the needed
                  # instance types triples files.
                  for i in ${comp_languages[@]}
                  do
                      # Create the base directory for each language used as complement
                      if [ -e $DIRECTORY_PREFIX$i ]; then
                          echo $DIRECTORY_PREFIX$i + ' already exists.'
                      else
                          create_dir $DIRECTORY_PREFIX$i
                      fi
                      # Create the TDB directory related to each language used as complement
                      if [ -e $DIRECTORY_PREFIX$i ]; then
                          echo $DIRECTORY_PREFIX$i + ' already exists.'
                      else
                          create_dir $DIRECTORY_PREFIX$i + '/TDB/' + $i
                      fi
                      mkdir $dbpedia_workspace/dbpedia_data/original/dbpedia/$lang_i18n/TDB/$lang_i18n
                      download_file http://downloads.dbpedia.org/$dbpedia_version/$i/instance_types_$i.nt.bz2 $i
                      bunzip2 $DIRECTORY_PREFIX$i/instance_types_$i.nt.bz2
                  done
                  $comp_types=1
                  break;;
            No ) break;;
        esac
    done
}

#+------------------------------------------------------------------------------------------------------------------------------+
#| Main                                                                                                                         |
#+------------------------------------------------------------------------------------------------------------------------------+
set -e

# Creating all the directories needed
echo 'Creating directories...'
create_dir $dbpedia_workspace/dbpedia_data
create_dir $dbpedia_workspace/dbpedia_data/original
create_dir $dbpedia_workspace/dbpedia_data/original/wikipedia/
create_dir $dbpedia_workspace/dbpedia_data/original/wikipedia/$lang_i18n
create_dir $dbpedia_workspace/dbpedia_data/original/dbpedia
create_dir $dbpedia_workspace/dbpedia_data/original/dbpedia/$lang_i18n
create_dir $dbpedia_workspace/dbpedia_data/data
create_dir $dbpedia_workspace/dbpedia_data/data/output
create_dir $dbpedia_workspace/dbpedia_data/data/opennlp
create_dir $dbpedia_workspace/dbpedia_data/data/opennlp/$language

# After creating the directories we can ask the user if he wants to complement the instance types triples file.
# The idea is to improve the indexing of a language using other languages. This is optional.
complement_types $lang

# The next step is to download all the needed files.
set +e

echo 'Getting DBpedia Files...'
download_file http://downloads.dbpedia.org/$dbpedia_version/$lang_i18n/labels_$lang_i18n.nt.bz2 $lang_i18n
download_file http://downloads.dbpedia.org/$dbpedia_version/$lang_i18n/redirects_$lang_i18n.nt.bz2 $lang_i18n
download_file http://downloads.dbpedia.org/$dbpedia_version/$lang_i18n/disambiguations_$lang_i18n.nt.bz2 $lang_i18n
download_file http://downloads.dbpedia.org/$dbpedia_version/$lang_i18n/instance_types_$lang_i18n.nt.bz2 $lang_i18n

echo 'Getting Wikipedia Dump...'
download_file "http://dumps.wikimedia.org/"$lang_i18n"wiki/latest/"$lang_i18n"wiki-latest-pages-articles.xml.bz2" $lang_i18n

echo 'Getting LingPipe Spotter...'
download_file http://dbp-spotlight.svn.sourceforge.net/viewvc/dbp-spotlight/tags/release-0.5/dist/src/deb/control/data/usr/share/dbpedia-spotlight/spotter.dict

echo 'Getting Spot Selector...'
download_file http://spotlight.dbpedia.org/download/release-0.5/spot_selector.tgz

echo 'Getting Index Files...'
download_file http://dbp-spotlight.svn.sourceforge.net/viewvc/dbp-spotlight/tags/release-0.5/dist/src/deb/control/data/usr/share/dbpedia-spotlight/index.tgz
download_file http://dbp-spotlight.svn.sourceforge.net/viewvc/dbp-spotlight/tags/release-0.5/dist/src/deb/control/data/usr/share/dbpedia-spotlight/pos-en-general-brown.HiddenMarkovModel

echo 'Getting Apache OpenNLP models...'
dl_opennlp_file $lang_i18n "chunker"
dl_opennlp_file $lang_i18n "location"
dl_opennlp_file $lang_i18n "organization"
dl_opennlp_file $lang_i18n "person"
dl_opennlp_file $lang_i18n "pos-maxent"
dl_opennlp_file $lang_i18n "sent"
dl_opennlp_file $lang_i18n "token"
echo 'done!'

#------------------------------------- Runtime Files --------------------------------------------------
mv spotter.dict $dbpedia_workspace/dbpedia_data/data
mv pos-en-general-brown.HiddenMarkovModel $dbpedia_workspace/dbpedia_data/data
#index
tar xvf index.tgz
mv index $dbpedia_workspace/dbpedia_data/data/output
#spot selector
tar xvf spot_selector.tgz
mv spotsel $dbpedia_workspace/dbpedia_data/data
#Moving OpenNLP files
mv $lang_i18n-chunker.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-ner-location.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-ner-organization.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-ner-person.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-pos-maxent.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-sent.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language
mv $lang_i18n-token.bin $dbpedia_workspace/dbpedia_data/data/opennlp/$language

#------------------------------------- Original Data  --------------------------------------------------
mv index.tgz  $dbpedia_workspace/dbpedia_data/original
mv spot_selector.tgz  $dbpedia_workspace/dbpedia_data/original




