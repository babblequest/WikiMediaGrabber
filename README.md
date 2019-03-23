# WikiMediaGrabber

Grab wiki media files from wikipedia archival sites. 

Taking some pains to avoid partial reads, support warm starts and most importantly be polite.

I have found these resources to be extreamly useful for creating NLP corpora from scratch.

## To build the utility
    mvn  clean compile assembly:single

## To run the utility
    java -jar  wikiarchiver-1.0-SNAPSHOT-jar-with-dependencies.jar -v -a ftpmirror.your.org -i /pub/wikimedia/dumps -o archive -u anonymous -e user@email.com -s "-pages-articles.xml.bz2"
  
If this utility is prematurly interupted it will rescan the directories and not pull resources that already exist.

Timeouts have been placed in the program to avoid saturating the site.

If you are using wikimedia sources for real work please feel free to donate. 
Typical fees from the Lexical Data Consotium run from $100 to $1000 to put things in perspective.

https://donate.wikimedia.org/w/index.php?title=Special:LandingPage&country=US&uselang=en&utm_medium=spontaneous&utm_source=fr-redir&utm_campaign=spontaneous
 
