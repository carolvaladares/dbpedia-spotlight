# You are expected to run the commands in this script from inside the bin directory in your DBpedia Spotlight installation
# Adjust the paths here if you don't. This script is meant more as a step-by-step guidance than a real automated run-all.
# If this is your first time running the script, we advise you to copy/paste commands from here, closely watching the messages
# and the final output.
#
# @author maxjakob, pablomendes

export DBPEDIA_WORKSPACE=C:/Users/Renan/Documents/dbpedia_data

export INDEX_CONFIG_FILE=../conf/indexing.properties
export lang_i18n=pt

JAVA_XMX=2g


# you have to run maven2 from the module that contains the indexing classes
cd ../index
# the indexing process will generate files in the directory below
if [ -e $DBPEDIA_WORKSPACE/data/output  ]; then
    echo "$DBPEDIA_WORKSPACE"'/data/output already exist.'
else
	#mkdir -p C:/Users/Renan/Documents/dbpedia_data/data/output
    mkdir -p $DBPEDIA_WORKSPACE/data/output
fi

# first step is to extract valid URIs, synonyms and surface forms from DBpedia
mvn scala:run -Dlauncher=ExtractCandidateMap "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE"
#mvn scala:run -Dlauncher=ExtractCandidateMap "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties"

# now we collect parts of Wikipedia dump where DBpedia resources occur and output those occurrences as Tab-Separated-Values
echo -e "Parsing Wikipedia dump to extract occurrences...\n"
mvn scala:run -Dlauncher=ExtractOccsFromWikipedia "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|$DBPEDIA_WORKSPACE/data/output/occs.tsv"
#mvn scala:run -Dlauncher=ExtractOccsFromWikipedia "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties|C:/Users/Renan/Documents/dbpedia_data/data/output/occs.tsv"

# (recommended) sorting the occurrences by URI will speed up context merging during indexing
echo -e "Sorting occurrences to speed up indexing...\n"
sort -t$'\t' -k2 C:/Users/Renan/Documents/dbpedia_data/data/output/occs.tsv >C:/Users/Renan/Documents/dbpedia_data/data/output/occs.uriSorted.tsv
#sort -t$'\t' -k2 C:/Users/Renan/Documents/dbpedia_data/data/output/occs.tsv > /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/occs.uriSorted.tsv
#sort -t$'\t' -k2 $DBPEDIA_WORKSPACE/data/output/occs.tsv >$DBPEDIA_WORKSPACE/data/output/occs.uriSorted.tsv

# (optional) preprocess surface forms however you want: produce acronyms, abbreviations, alternative spellings, etc.
#            in the example below we scan paragraphs for uri->sf mappings that occurred together more than 3 times.
echo -e "Extracting Surface Forms...\n"
cat $DBPEDIA_WORKSPACE/data/output/occs.uriSorted.tsv | cut -d$'\t' -f 2,3 |  perl -F/\\t/ -lane 'print "$F[1]\t$F[0]";' > $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs.tsv
#cat /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/occs.uriSorted.tsv | cut -d$'\t' -f 2,3 |  perl -F/\\t/ -lane 'print "$F[1]\t$F[0]";' > /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs.tsv

sort $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs.tsv | uniq -c > $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs.count
#sort C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs.tsv | uniq -c > /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs.count

grep -Pv "      [123] " $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs.count | sed -r "s|\s+[0-9]+\s(.+)|\1|" > $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs-thresh3.tsv
#grep -Pv "      [123] " C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs.count | sed -r "s|\s+[0-9]+\s(.+)|\1|" > /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs-thresh3.tsv

cp $DBPEDIA_WORKSPACE/data/output/surfaceForms.tsv $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromTitRedDis.tsv
#cp C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms.tsv C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromTitRedDis.tsv

cat $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromTitRedDis.tsv $DBPEDIA_WORKSPACE/data/output/surfaceForms-fromOccs.tsv > $DBPEDIA_WORKSPACE/data/output/surfaceForms.tsv
#cat C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromTitRedDis.tsv C:/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms-fromOccs.tsv > /cygdrive/c/Users/Renan/Documents/dbpedia_data/data/output/surfaceForms.tsv

# now that we have our set of surfaceForms, we can build a simple dictionary-based spotter from them
mvn scala:run -Dlauncher=IndexLingPipeSpotter "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE"
#mvn scala:run -Dlauncher=IndexLingPipeSpotter "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties"

set -e
# create a lucene index out of the occurrences
echo -e "Creating a context index from occs.tsv...\n"
mvn scala:run -Dlauncher=IndexMergedOccurrences "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|$DBPEDIA_WORKSPACE/data/output/occs.uriSorted.tsv"
#mvn scala:run -Dlauncher=IndexMergedOccurrences "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties|C:/Users/Renan/Documents/dbpedia_data/data/output/occs.uriSorted.tsv"

# NOTE: if you get an out of memory error from the command above, try editing ../index/pom.xml with correct jvmArg and file arguments, then run:
#mvn scala:run -Dlauncher=IndexMergedOccurrences "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|$DBPEDIA_WORKSPACE/data/output/occs.uriSorted.tsv"

# (optional) make a backup copy of the index before you lose all the time you've put into this
#cp -R $DBPEDIA_WORKSPACE/data/output/index $DBPEDIA_WORKSPACE/data/output/index-backup
# add surface forms to index
echo -e "Adding Surface Forms to index...\n"
 mvn scala:run -Dlauncher=AddSurfaceFormsToIndex "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|$DBPEDIA_WORKSPACE/data/output/index"
 #mvn scala:run -Dlauncher=AddSurfaceFormsToIndex "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties|C:/Users/Renan/Documents/dbpedia_data/data/output/index"
# or
 mvn scala:run -Dlauncher=CandidateIndexer "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$DBPEDIA_WORKSPACE/data/output/surfaceForms.tsv|$DBPEDIA_WORKSPACE/data/output/candidateIndex|3|case-insensitive|overwrite"

# (optional) complement the entity types with another language
if [ -e $DBPEDIA_WORKSPACE/dbpedia_data/original/dbpedia/$lang_i18n/TDB  ]; then
    echo -e "Complementing types...\n"
    mvn scala:run -Dlauncher=ComplementTypes "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=E:/instance_types_$lang_i18n.nt|E:/instance_types_en.nt|E:/$lang_i18n_en_links.nt|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/TDB/$lang_i18n|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/TDB/en|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/instance_types_$lang_i18n_en.nt|C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties"
    #mvn scala:run -Dlauncher=ComplementTypes "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/instance_types_$lang_i18n.nt|$DBPEDIA_WORKSPACE/original/dbpedia/en/instance_types_en.nt|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/$lang_i18n_en_links.nt|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/TDB/$lang_i18n|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/TDB/en|$DBPEDIA_WORKSPACE/original/dbpedia/$lang_i18n/instance_types_$lang_i18n.nt|C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties"
fi

# add entity types to index
mvn scala:run -Dlauncher=AddTypesToIndex "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|$DBPEDIA_WORKSPACE/data/output/index-withSF"
#mvn scala:run -Dlauncher=AddTypesToIndex "-DjavaOpts.Xmx=4g" "-DaddArgs=C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties|C:/Users/Renan/Documents/dbpedia_data/data/output/index-withSF"

# (optional) reduce index size by unstoring fields (attention: you won't be able to see contents of fields anymore)
mvn scala:run -Dlauncher=CompressIndex "-DjavaOpts.Xmx=$JAVA_XMX" "-DaddArgs=$INDEX_CONFIG_FILE|10|$DBPEDIA_WORKSPACE/data/output/index-withSF-withTypes"
set +e

# train a linker (most simple is based on similarity-thresholds)
# mvn scala:run -Dlauncher=EvaluateDisambiguationOnly "-DjavaOpts.Xmx=$JAVA_XMX"
#mvn scala:run -Dlauncher=EvaluateDisambiguationOnly "-DjavaOpts.Xmx=4g"
