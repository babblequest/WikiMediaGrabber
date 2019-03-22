package com.babblequest.wikiarchiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;

public class WikiMediaGrabber
{
  private boolean verbose = false;

  private FTPClient ftp = null;
  private HashMap<String, String> existingFiles = new HashMap<String,String>();

  final String VERBOSE = "verbose";
  final String SITEADDRESS = "site";
  final String INPATH = "inpath";
  final String OUTPATH = "outpath";
  final String USERNAME = "username";
  final String EMAIL = "email";
  final String SUFFIX = "suffix";
  final String HELP = "help";

  public Collection<String> extractLinks(String htmlBody)
  {
    Collection<String> links = new ArrayList<String>();

    try {
      Parser parser = new Parser();
      parser.setInputHTML(htmlBody);
      NodeList list = parser.extractAllNodesThatMatch(new NodeClassFilter (LinkTag.class));

      for (int i = 0; i < list.size (); i++){
        LinkTag extracted = (LinkTag)list.elementAt(i);
        String extractedLink = extracted.getLink();
        links.add(extractedLink);
      }

    } catch (Exception e) {

      e.printStackTrace();
    }
    return(links);
  }

  public FTPClient openSite(String site, String username, String password)
      throws IOException
  {
    System.out.println("Opening site " + site);
    boolean isConnected = false;

    ftp = new FTPClient();

    try
    {
      FTPClientConfig config = new FTPClientConfig();
      //config.setXXX(YYY); // change required options
      ftp.configure(config );

      for (int i=0;i<30 && !isConnected;i++)
      { 
        boolean error = false;
        int reply;
        ftp.connect(site);
        System.out.println("Logging in");
        ftp.login(username, password);

        System.out.println("Setting file types to binary");
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        System.out.print(ftp.getReplyString());

        // After connection attempt, you should check the reply code to verify
        // success.
        System.out.println("Checking connection status");
        reply = ftp.getReplyCode();

        if(FTPReply.isPositiveCompletion(reply)) {
          System.out.println("Successful Connection to ftp site");
          isConnected = true;

          ftp.enterLocalPassiveMode();
          ftp.setBufferSize(1000000);
        }
        else
        {
          System.out.println("Trying connection again");
          try
          {
            Thread.sleep(10000);
          }
          catch (InterruptedException e)
          {
          }
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
      throw(e);
    }

    return(ftp);
  }

  public void closeSite()
      throws IOException
  {
    ftp.logout();
    if (ftp.isConnected()) 
      ftp.disconnect();

    ftp.disconnect();
  }

  public LinkedList<String> grabList(String directory, String pattern, int fileType)
      throws IOException
  {
    LinkedList<String> list = new LinkedList<String>();

    FTPFile[] files = ftp.listFiles(directory);

    System.out.println("file list ");
    for (int i=0;i<files.length;i++)
      System.out.println(files[i].getName());

    for (int i=0;i<files.length;i++)
      if (files[i].getType() == fileType)
        if (files[i].getName().matches(pattern))
          list.add(files[i].getName());
    return(list);
  }

  public boolean grabFile(String directory,String pattern, String storageDir, String top, String date)
      throws IOException
  {
    boolean downloaded = false;

    String previousDate = existingFiles.get(top);
    if (previousDate != null)
    {
      System.out.println("Already have file from another date forcing date");
      date = previousDate;
    }

    directory = directory + "/" + top + "/" + date;

    FTPFile[] files = ftp.listFiles(directory);
    System.out.println("files in this diretory ");
    for (int i=0;i<files.length;i++)
      System.out.println(files[i].getName());

    String name;

    for (int i=0;i<files.length;i++)
      if (files[i].getType() == FTPFile.FILE_TYPE)
        if (files[i].getName().endsWith(pattern))  // may be more then one file
        {
          name = files[i].getName();

          long ftpSize = files[i].getSize();

          File file = new File(storageDir + "/" + name);
          long fileSize = file.length();

          System.out.println("grabbing file " + name + " expected file length = " + ftpSize + " old file length " + fileSize);

          if (!file.exists() || (fileSize < ftpSize))
          {
            InputStream inputStream = ftp.retrieveFileStream(directory + "/" + name);
            FileOutputStream fileOutputStream = new FileOutputStream(storageDir + "/" + name);
            //Using org.apache.commons.io.IOUtils
            IOUtils.copy(inputStream, fileOutputStream);
            fileOutputStream.flush();
            IOUtils.closeQuietly(fileOutputStream);
            IOUtils.closeQuietly(inputStream);
            boolean commandOK = ftp.completePendingCommand();

            downloaded = true;
          }
          else
            System.out.println("file already exists");
        }
    return(downloaded);
  }

  public HashMap<String, String> getExistingFiles(String directory, String suffix)
      throws IOException
  {
    File dir = new File(directory);
    File files[];

    if (dir.isDirectory())
    {
      System.out.println("\nD: " + dir.getAbsolutePath());
      files = dir.listFiles();

      for (int i=0;i<files.length;i++)
      {
        String filename = files[i].getName();
        if (filename.matches("[^-]+\\-([0-9]{6}).*" + suffix)) 
        {
          String parts[] = filename.split("-");
          String top = parts[0];
          String date = parts[1]; 
          existingFiles.put(top,date);
        }
      }
    }
    System.out.println("existing files " + existingFiles);
    return(existingFiles);
  }



  private Options generateOptions()
  {
    final Option helpOption = Option.builder("h")
        .required(false)
        .hasArg(false)
        .longOpt(HELP)
        .desc("Get help")
        .build();
    final Option verboseOption = Option.builder("v")
        .required(false)
        .hasArg(false)
        .longOpt(VERBOSE)
        .desc("Print status with verbosity.")
        .build();
    final Option siteOption = Option.builder("a")
        .longOpt(SITEADDRESS)
        .hasArg()
        .desc("DNS name for site ie. ftpmirror.your.org")
        .build();
    final Option inpathOption = Option.builder("i")
        .longOpt(INPATH)
        .hasArg()
        .desc("Input Path ie. /pub/wikimedia/dumps")
        .build();
    final Option outpathOption = Option.builder("o")
        .longOpt(OUTPATH)
        .hasArg()
        .desc("DNS name for site ie. ftpmirror.your.org")
        .build();
    final Option userOption = Option.builder("u")
        .longOpt(USERNAME)
        .hasArg()
        .desc("Ftp user name typically (anonymous)")
        .build();
    final Option emailOption = Option.builder("e")
        .longOpt(EMAIL)
        .hasArg()
        .desc("Anonymous ftp email address")
        .build();
    final Option suffixOption = Option.builder("s")
        .longOpt(SUFFIX)
        .hasArg()
        .desc("File suffix ie. \"-pages-articles.xml.bz2\"")
        .build();

    final Options options = new Options();
    options.addOption(verboseOption);
    options.addOption(siteOption);
    options.addOption(inpathOption);
    options.addOption(outpathOption);
    options.addOption(userOption);
    options.addOption(emailOption);
    options.addOption(suffixOption);
    options.addOption(helpOption);

    return options;
  }

  private CommandLine parseCommandLine(String args[])
  {
    Options options = generateOptions();

    CommandLineParser cmdLineParser = new DefaultParser();
    CommandLine commandLine = null;
    try
    {
      commandLine = cmdLineParser.parse(options, args);

      HelpFormatter formatter;
      if (commandLine.hasOption(HELP))
      {
        formatter = new HelpFormatter();
        formatter.printHelp( "WikiMediaGrabber", options );
        System.out.println("Typical usage is: -v -a ftpmirror.your.org -i /pub/wikimedia/dumps -o archive -u anonymous -e sbparke@comcast.net -s \"-pages-articles.xml.bz2\"");
        System.exit(0);
      }
    }    

    catch (ParseException parseException)
    {
      System.out.println(
          "ERROR: Unable to parse command-line arguments "
              + Arrays.toString(args) + " due to: "
              + parseException);
    }

    return(commandLine);
  }

  public void checkParam(String param, String message)
  {
    if (param == null)
    {
      System.err.println(message);
      System.exit(1);
    }
  }

  public void run(String args[])
  {
    WikiMediaGrabber grabber = new WikiMediaGrabber();

    CommandLine commandLine = grabber.parseCommandLine(args);

    verbose = commandLine.hasOption(VERBOSE);

    final String site = commandLine.getOptionValue(SITEADDRESS);
    final String topLevel = commandLine.getOptionValue(INPATH);
    final String outputDir = commandLine.getOptionValue(OUTPATH);
    final String username = commandLine.getOptionValue(USERNAME);
    final String email = commandLine.getOptionValue(EMAIL);
    final String suffix = commandLine.getOptionValue(SUFFIX);

    checkParam(site,"You must specify the ftp site address.");
    checkParam(topLevel, "You must specify the top level directory to scan");
    checkParam(outputDir, "You must specify an output directory");
    checkParam(username, "You must specify a username for the ftp site");
    checkParam(email, "You must specify an email address for this site");
    checkParam(suffix, "You must specify a file suffix to match files");

    if (Files.notExists(Paths.get(outputDir)))
    {
      System.err.println("Output directory does not exist: " + outputDir);
      System.exit(1);
    }

    try
    {
      getExistingFiles(outputDir,suffix);

      // open site with username and password
      openSite(site,username,email);

      // Get highest level
      LinkedList<String> top = grabList(topLevel,"[a-z]+(wiki|wikisource)",FTPFile.DIRECTORY_TYPE);

      System.out.println("Top level list " + top); 
      //    Go to each subdirectory and pull date directories
      int downloaded = 1;

      for (int i=0;i<top.size();i++)
      { 
        // Skip langauge files that already exist in output directory
        System.out.println("Getting language file " + topLevel + "/" + top.get(i));
        LinkedList<String> dates = grabList(topLevel + "/" + top.get(i), "[0-9]+", FTPFile.DIRECTORY_TYPE);
        if (dates.size() > 3)
        {
          Collections.sort(dates);

          System.out.println("Sorted dates " + dates);

          //  Go to 3rd youngest file to avoid partial files
          String bestDate = dates.get(dates.size() - 3);

          //  Pull xml file with specified suffix
          System.out.println("grabbing actual file " + topLevel + "/" + top.get(i) + "/" + bestDate);
          if (grabFile(topLevel,suffix, outputDir , top.get(i), bestDate))
            downloaded++;
        }

        if (downloaded%10 == 0)
        {
          System.out.println("being polite to other grabbers");
          closeSite();
          Thread.sleep(14000);
          openSite(site,username,email);
        }
      }
      closeSite();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public static void main(String args[])
  {
    try
    {
      WikiMediaGrabber grabber = new WikiMediaGrabber();
      grabber.run(args);
    }
    catch (Exception e)
    {
      System.out.println("WikiMediaGrabber failed due to exception ");
      e.printStackTrace();
    }
  }
}
