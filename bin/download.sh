#!/bin/bash
#+------------------------------------------------------------------------------------------------------------------------------+
#| DBpedia Spotlight - Download script                                                                                          |
#| @author @sandroacoelho @zaknarfen                                                                                            |
#+------------------------------------------------------------------------------------------------------------------------------+
PROGNAME=$(basename $0)

##### Config parameters (adjust according your target language and folder)

export lang_i18n=pt
export language=portuguese
export comp_languages=(it es en)
export dbpedia_workspace="E:/Spotlight"
#export dbpedia_workspace=/var/local/Spotlight
export dbpedia_version=3.8
RELEASE_VERSION="0.5"

# Paths to all the directories we are going to need
DATA=$dbpedia_workspace/data
DBPEDIA_DATA=$DATA/dbpedia
JENA_DATA=$DATA/jena
OPENNLP_DATA=$DATA/opennlp
OUTPUT_DATA=$DATA/output
WIKIPEDIA_DATA=$DATA/wikipedia
RESOURCES_DATA=$DATA/resources

# All the download URLs used
OPENNLP_DOWNLOADS="http://opennlp.sourceforge.net/models-1.5"
DBPEDIA_DOWNLOADS="http://downloads.dbpedia.org"
SOURCEFORGE_DOWNLOADS="http://dbp-spotlight.svn.sourceforge.net/viewvc/dbp-spotlight/tags/release-"$RELEASE_VERSION"/dist/src/deb/control/data/usr/share/dbpedia-spotlight"
SPOTLIGHT_DOWNLOADS="http://spotlight.dbpedia.org/download/release-"$RELEASE_VERSION
GITHUB_DOWNLOADS="--no-check-certificate https://github.com/sandroacoelho/lucene-quickstarter/blob/master"
WIKIMEDIA_DOWNLOADS="http://dumps.wikimedia.org/"$lang_i18n"wiki/latest"

#+------------------------------------------------------------------------------------------------------------------------------+
#| Functions                                                                                                                    |
#+------------------------------------------------------------------------------------------------------------------------------+

# Error_exit function by William Shotts. http://stackoverflow.com/questions/64786/error-handling-in-bash
function error_exit
{
    echo -e "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

# The function used to create all the directories needed
function create_dir()
{
    if [ -e $1 ]; then
        echo -e $1" already exists. Skipping creating this directory!"
    else
        mkdir $1
    fi
}

# A helper function to download files from a given path. The first parameter is the path from where to download the file
# without the file name, the second states the file name, and the third is where to save that file
function download_file()
{
    # Only downloads if there is no current file or there is a newer version
    echo "$#"
    case "$#" in
        "3")
            wget -q --spider $1/$2
            if [ $? -eq 0 ] ; then
                wget -N $1/$2 --directory-prefix=$3
            else
                # The file can't be found. We can extract a substring with the file name and show it to the user
                error_exit "ERROR: The file '"$2"' cannot be found for download.\nYou can change to another language and rerun this script or comment the download command for this file if you already have it inside the respective directory."
            fi
            ;;
        *)
            error_exit "ERROR: Incorrect number of parameters!";
    esac
    echo -e "done!\n"
}

# A slightly modified version of the download function. This time if we don't find the initial language files
# we change the path to get the english OpenNLP files
function dl_opennlp_file()
{
    local path=$OPENNLP_DOWNLOADS/$1'-'$2
    wget -q --spider $path
    if [ $? -eq 0 ] ; then
        wget -N $path --directory-prefix=$3
    else
        echo "$file not found. Getting an English version..."
        local string_to_replace=$1'-'
        local new_path=$(echo $path | sed -e s/"$string_to_replace"/en-/g)
        echo $new_path
        wget -q --spider $new_path
        if [ $? -eq 0 ] ; then
            wget -N $new_path --directory-prefix=$3
        else
            string_to_replace="en-"
            new_path=$(echo $new_path | sed -e s/"$string_to_replace"/en-ner-/g)
            wget -N $new_path --directory-prefix=$3
        fi
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
       if ([ $(expr length $i) -ne 2 ] | [ "$i" == "$lang_i18n" ] | [ $( expr match $i [a-zA-Z]\. ) -ne 2 ]); then
           error_exit "ERROR: Invalid complement languages!"
       fi
    done
}

# Just some initial processing for the types complement task
function init_complement
{
    # We will decompress the instance types file to a .nt format so we can use the FileManager class from Apache Jena. The original
    # compressed file will be preserved in the process
    bunzip2 -fk $DBPEDIA_DATA/$lang_i18n/instance_types_$lang_i18n.nt.bz2 > $DBPEDIA_DATA/$lang_i18n/instance_types_$lang_i18n.nt

    # Removing the last line of the file if there is a "completed" message in the end
    sed '/\(\# completed\)/d' $DBPEDIA_DATA/$lang_i18n/instance_types_$lang_i18n.nt > $DBPEDIA_DATA/$lang_i18n/tmp.nt
    mv $DBPEDIA_DATA/$lang_i18n/tmp.nt $DBPEDIA_DATA/$lang_i18n/instance_types_$lang_i18n.nt

    # Create a directory to keep the TDB store of the initial language. This way we do not have to load everything to memory in
    # order to execute SPARQL queries
    create_dir $JENA_DATA/$lang_i18n/TDB

    # We will also download the bijective interlanguage links file of the initial language. This file has 'sameAs' relations that can
    # be used to find types for a resource in another language
    download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$lang_i18n interlanguage_links_same_as_$lang_i18n.nt.bz2 $DBPEDIA_DATA/$lang_i18n
    bunzip2 -fk $DBPEDIA_DATA/$lang_i18n/interlanguage_links_same_as_$lang_i18n.nt.bz2 > $DBPEDIA_DATA/$lang_i18n/interlanguage_links_same_as_$lang_i18n.nt
}

# The function used in case the user wants to complement the instance types triples file
function complement_types()
{
    echo -e "\nDo you want to complement the '"$language"' instance types file with other languages? (optional)\nThis file is used in the indexing stage.\n"
    select yn in "Yes" "No"; do
        case $yn in    
            Yes ) # Test if the complement languages array is empty or not defined, and valid
                  [[ $comp_languages && ${comp_languages-x} ]] && test_comp_lang $comp_languages || error_exit "Complement languages array not defined or empty!"
                  # Start to set up the complement types stage by creating the directories needed and downloading all the respective files
                  # for the initial language
                  init_complement;
                  # Do the same with all the complement languages
                  for i in ${comp_languages[@]}
                  do
                      # Create the base directory for each language used as complement
                      create_dir $DBPEDIA_DATA/$i
                      # Create the directory for the TDB store
                      create_dir $JENA_DATA/$i
                      create_dir $JENA_DATA/$i/TDB
                      # Download and unzip the file we are going to query over
                      download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$i instance_types_$i.nt.bz2 $DBPEDIA_DATA/$i
                      bunzip2 -fk $DBPEDIA_DATA/$i/instance_types_$i.nt.bz2 > $DBPEDIA_DATA/$i/instance_types_$i.nt
                      sed '/\(\# completed\)/d' $DBPEDIA_DATA/$i/instance_types_$i.nt > $DBPEDIA_DATA/$i/tmp.nt
                      mv $DBPEDIA_DATA/$i/tmp.nt $DBPEDIA_DATA/$i/instance_types_$i.nt
                  done
                  break;;
            No ) break;;
        esac
    done
}

#+------------------------------------------------------------------------------------------------------------------------------+
#| Main                                                                                                                         |
#+------------------------------------------------------------------------------------------------------------------------------+

# Create the installation directory
mkdir $dbpedia_workspace
touch $dbpedia_workspace/foo && rm -f $dbpedia_workspace/foo || error_exit "ERROR: The directory '$dbpedia_workspace' is not writable! Change its permissions or choose another 'dbpedia_workspace' in download.sh"

set -e

# Creating all the directories needed
echo -e "\nCreating directories..."
create_dir $DATA
create_dir $WIKIPEDIA_DATA
create_dir $WIKIPEDIA_DATA/$lang_i18n
create_dir $DBPEDIA_DATA
create_dir $DBPEDIA_DATA/$lang_i18n
create_dir $OUTPUT_DATA
create_dir $OPENNLP_DATA
create_dir $OPENNLP_DATA/$lang_i18n
create_dir $JENA_DATA
create_dir $JENA_DATA/$lang_i18n
create_dir $RESOURCES_DATA
create_dir $RESOURCES_DATA/$lang_i18n

# The next step is to download all the needed files.
set +e

echo -e "\nGetting DBpedia Files..."
# The download_file function parameters are: 1) path/to/file 2) file_name 3) where/to/save
download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$lang_i18n labels_$lang_i18n.nt.bz2 $DBPEDIA_DATA/$lang_i18n
download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$lang_i18n redirects_$lang_i18n.nt.bz2 $DBPEDIA_DATA/$lang_i18n
download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$lang_i18n disambiguations_$lang_i18n.nt.bz2 $DBPEDIA_DATA/$lang_i18n
download_file $DBPEDIA_DOWNLOADS/$dbpedia_version/$lang_i18n instance_types_$lang_i18n.nt.bz2 $DBPEDIA_DATA/$lang_i18n

# After creating the directories we can ask the user if he wants to complement the instance types triples file.
# The idea is to improve the indexing stage of a language using other languages. This is optional.
complement_types $lang_i18n

echo "Getting Wikipedia Dump..."
download_file $WIKIMEDIA_DOWNLOADS $lang_i18n"wiki-latest-pages-articles.xml.bz2" $WIKIPEDIA_DATA/$lang_i18n
bunzip2 -fk $WIKIPEDIA_DATA/$lang_i18n/$lang_i18n"wiki-latest-pages-articles.xml.bz2" > $WIKIPEDIA_DATA/$lang_i18n/$lang_i18n"wiki-latest-pages-articles.xml"

echo "Getting LingPipe Spotter..."
download_file $SOURCEFORGE_DOWNLOADS "spotter.dict" $RESOURCES_DATA

echo "Getting Spot Selector..."
download_file $SPOTLIGHT_DOWNLOADS "spot_selector.tgz" $RESOURCES_DATA

echo "Getting Index Files..."
download_file $SOURCEFORGE_DOWNLOADS "index.tgz" $RESOURCES_DATA
tar -xvf $RESOURCES_DATA/index.tgz --force-local -C $RESOURCES_DATA
rm $RESOURCES_DATA/index.tgz
download_file $SOURCEFORGE_DOWNLOADS "pos-en-general-brown.HiddenMarkovModel" $RESOURCES_DATA
tar -xvf $RESOURCES_DATA/spot_selector.tgz --force-local -C $RESOURCES_DATA
rm $RESOURCES_DATA/spot_selector.tgz

echo "Getting the stop words list..."
download_file "$GITHUB_DOWNLOADS"/$lang_i18n "stopwords.list" $RESOURCES_DATA/$lang_i18n

echo "Getting the URI blacklisted patterns list..."
download_file "$GITHUB_DOWNLOADS"/$lang_i18n "blacklistedURIPatterns.$lang_i18n.list" $RESOURCES_DATA/$lang_i18n

echo "Getting Apache OpenNLP models..."
# The download_opennlp_file function parameters are: 1) language 2) model_name 3) where/to/save
dl_opennlp_file $lang_i18n "chunker.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "location.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "organization.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "person.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "pos-maxent.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "sent.bin" $OPENNLP_DATA/$lang_i18n
dl_opennlp_file $lang_i18n "token.bin" $OPENNLP_DATA/$lang_i18n

echo -e "\nAll the downloads are done!"


