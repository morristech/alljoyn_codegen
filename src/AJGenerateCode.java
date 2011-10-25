/*******************************************************************************
 * Copyright 2010 - 2011, Qualcomm Innovation Center, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 ******************************************************************************/

import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import java.io.InputStream;

/**
 * The main class of the AllJoynCodeGenTool
 *
 */
public class AJGenerateCode {
    
    /**
     * The main function of the program.  It parses the command line and causes
     * the XML file(s) to be read and code to be output.
     * @param args
     */
    public static void main(String[] args) {
        ArrayList<String> inputFileNames = new ArrayList<String>();
        CodeGenConfig codeGenConfig = CodeGenConfig.getInstance();
	LogOutput UIOutput = new LogOutput("AJGenerateCode", codeGenConfig.programName);

        /*
         * If there are no command line arguments print the usage message as
         * some are required.
         */
        if (args.length == 0) {
            printUsage(codeGenConfig.programName, 0);
        }

        /*
         * Parse the command line arguments
         */
        int i = 0;
        String temp;
        boolean wellknownNameProvided = false;
        // iterate over the list.
        while(i < args.length){
            // -h
            if (args[i].equals("-h") || args[i].equals("--help")) {
                printUsage(codeGenConfig.programName, 0);
            }
            // -l
            else if (args[i].equals("-l") || args[i].equals("--lax-naming")) {
                codeGenConfig.useStrict = false;
            }
            // -u
            else if (args[i].equals("-u") || args[i].equals("--user-output")) {
                i++;
		temp = args[i].toLowerCase();
		if(temp.equals("f")){
		    UIOutput.SetLogLevel(LogOutput.LogLevel.fatal);
		} else if(temp.equals("e")){
		    UIOutput.SetLogLevel(LogOutput.LogLevel.error);
		} else if(temp.equals("w")){
		    UIOutput.SetLogLevel(LogOutput.LogLevel.warning);
		} else if(temp.equals("i")){
		    UIOutput.SetLogLevel(LogOutput.LogLevel.inform);
		} else if(temp.equals("d")){
		    UIOutput.SetLogLevel(LogOutput.LogLevel.detail);
		} else UIOutput.LogFatal("[-u option] " +
                	"invalid logging level character.", 0);
            }
            // -b
            else if (args[i].equals("-b") || args[i].equals("--object-path")) {
                i++;
                temp = args[i].substring(args[i].indexOf('=') + 1); //unnecessary?

		// get object path first
		int slashIndex = args[i].lastIndexOf("/");
		if(-1 == slashIndex) {
			UIOutput.LogFatal("[-b option] object " +
                    		"path is invalid.", 0);
		}

                codeGenConfig.objPath = args[i].substring(0, slashIndex);
                if(!codeGenConfig.objPath.startsWith("/")){
                    codeGenConfig.objPath = "/" + codeGenConfig.objPath;
                }
                                
		temp = args[i].substring(slashIndex + 1);

                //make sure the name is printable, less than 200 char and does
                //not contain invalid chars. 
                if(!isPrintableName(temp)
                   || temp.contains("<")
                   || temp.contains(">")
                   || temp.contains("/")
                   || temp.contains("\\")
                   || temp.contains("?")
                   || temp.contains("\"")
                   || temp.contains(":")
                   || temp.contains("*")
                   || temp.contains("|")){
                    UIOutput.LogFatal("[-b option] object " +
                    		"name specified contains invalid character.", 0);
                }else if(temp.length() > 200){
                    UIOutput.LogFatal("[-b option] object " +
                			"name specified is too long. Object name cannot " +
                			"exceed 200 characters.", 0);
                }else{
                    codeGenConfig.className = temp;                	
                }
            }
            // -c
            else if (args[i].equals("-c") || args[i].equals("--client-only")){
                codeGenConfig.clientOnly = true;
            }
            // -p
            else if (args[i].equals("-p") || args[i].equals("--output-path")){
                i++;
                temp = args[i];
                if(!temp.endsWith("/")){
                    temp += "/";
                }
                
                //test the validity of the path
                File f = new File(temp);
                if(!f.exists() || !f.isDirectory()){
                    UIOutput.LogFatal("output path is not valid.", 0);
                }else if(!f.canWrite()){
                    UIOutput.LogFatal("the directory " +
                    		"specified in output_path is not writable.", 0);
                }else{
                    codeGenConfig.outputPath = temp;                	
                }
            }
            // -o
            else if (args[i].equals("-o") || args[i].equals("--overwrite")){
                codeGenConfig.overWrite = true;
            }
            // -e
            else if (args[i].equals("-e") ||
                     args[i].equals("--empty-elements")){
                codeGenConfig.allowEmpty = true;
            }
            // -R
            else if(args[i].equals("-R") || args[i].equals("--runnable")){
                codeGenConfig.runnable = true;
            }
            // -w
            else if(args[i].equals("-w") ||
                    args[i].equals("--well-known-name")){
            	i++;

            	wellknownNameProvided = validateWellKnownName(args[i], UIOutput);

            	codeGenConfig.wellKnownName = args[i]; 
            }
            // default case: assume the input file name
            else{
                inputFileNames.add(args[i]);
            }
            i++;
        } // while(there are command line arguments)

        /*
         * make sure that the required -w flag was set
         */
    	if (!wellknownNameProvided){
            UIOutput.LogFatal(
                "A well-known name used to connect to the bus " +
                "and advertising\n" +
                "\t\twas not provided.  A well-know name is required. Use " +
                "-w | --well-known-name option\n " +
                "\t\twhen running " + codeGenConfig.programName + ".", 0);
    	}

        /*
         * Make sure that file name was give, otherwise print usage message
         */
        if (inputFileNames.isEmpty()){
            UIOutput.LogFatal("Must include an input file name.\n", 0);
        }

        codeGenConfig.errorHandler = new AJNErrorHandler(codeGenConfig, UIOutput);
        try{
            //the AllJoynXMLParser takes in the string buffer of xml file and
            //creates the interface data structures 
            ParseAJXML alljoynParser = new ParseAJXML();
            
            String XMLClassDef; 
            for(i = 0; i < inputFileNames.size(); i++){
                XMLClassDef = ReadFiles.getStringFromFileSys(inputFileNames.get(i));
                alljoynParser.parseXML(XMLClassDef);            
            } 
            
            //create and write the output files
            for(int j = 0; j < codeGenConfig.interfaces.size(); j++) {
                WriteCode.config = codeGenConfig;
                WriteCode.inter = codeGenConfig.interfaces.get(j);
                WriteClientCode clientWriter = new WriteClientCode();
                clientWriter.writeHeaderFile();
                clientWriter.writeDevCCFile();
                clientWriter.writeCCFile();
                if(!codeGenConfig.clientOnly){
                    WriteServiceCode serviceWriter = new WriteServiceCode();
                    serviceWriter.writeHeaderFile();
                    serviceWriter.writeDevCCFile();
                    serviceWriter.writeCCFile();
                    if(j == 0) {
                        serviceWriter.writeMainFile();
                    }
                }
                
                if(j == 0) {
                    clientWriter.writeMainFile();
                    clientWriter.writeBusMgrFile(".h");
                    clientWriter.writeBusMgrFile(".cc");
                    clientWriter.writeMakeFile();
                }
            }
            
        }catch(SAXParseException e){
            codeGenConfig.errorHandler.error(e);
        }catch(Exception e){
            e.printStackTrace();
            UIOutput.LogFatal(e.getMessage(), 1);
        }
    } // main()

    /**
     * Print the usage message for the tool
     * @param programName: the name of the executable JAR file
     * @param exitValue: the exit value the use when calling system.exit()
     */
    public static void printUsage( String programName, int exitValue ) {
        /*
         * Print the usage string for the tool.
         */
        String usage = "AllJoyn Code Generator\n" + "  " + programName
            + " -h | --help\n\tDisplay this help\n" + "  " + programName;

        // get the rest of the usage string from the usage file
        usage += ReadFiles.getStringFromJAR("src/config/usage");

        // print the usage and exit with the value passed in
        System.out.println(usage);

        System.exit(exitValue);

    } // printUsage()


    /**
     * Check if the object name in cmd line is printable.
     * @param name: the neame being checked
     * @return true if the name is printable, false otherwise
     */
    public static boolean isPrintableName(String name) {
    	char c;
    	for(int i = 0; i < name.length(); i++){
            c = name.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
            if(Character.isISOControl(c)
               || c == KeyEvent.CHAR_UNDEFINED
               || block == null
               || block == Character.UnicodeBlock.SPECIALS){
                return false;
            }
    	}
    	return true;
    }// isPrintableName()

    /**
     * validate that the passed in name is correctly formatted.
     * @param name: the name to validate.
     */
    private static boolean validateWellKnownName(String name, LogOutput UIOutput) {
        if (name.contains(":")){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name cannot contain the ':' (colon) character.", 0);
        }
        if (name.startsWith(".")){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name cannot start with the '.' (period) character.", 0);
        }
        if(name.endsWith(".")){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name cannot end with a '.' (period).", 0);
        }
        //check that ".." is not used in the name
        if(name.contains("..")){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name must be composed of two or more\n " +
                     "\t\telements separated by a period ('.') character.  " +
                     "All elements must\n" +
                     "\t\tcontain at least one character.  Two period ('..') " +
                     "characters in a\n " +
                     "\t\trow is not allowed.", 0);
        }
        //check that none of the well-known name elements start with a digit
        Pattern pattern = Pattern.compile("\\.\\d");
        if (Character.isDigit(name.charAt(0)) || pattern.matcher(name).find()){
            UIOutput.LogFatal("[-w option] Each element in " +
            		"the well-known name must start\n" +
                    "\t\twith [A-Z],[a-z],'_' (underscore), or '-'(hyphen).", 0);
        }
        //check that there are at lease two elements. (letter + '.' + letter)
        pattern = Pattern.compile(".\\..");
        if(!pattern.matcher(name).find()){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name must be composed of two or more\n" +
                    "\t\telements separated by a period ('.') character. All " +
                    "elements must\n" +
                    "\t\tcontain at least one character.\n", 0);
        }
        if (name.length() > 255){
            UIOutput.LogFatal("[-w option] The well-known " +
            		"name has exceded the maximum name\n" +
                    "\t\tlength of 255 characters.", 0);
        }

        return true;

    } // validateWellKnownName()
    
} // class AllJoynCodeGenTool

/**
 * Extension of the Errorhandler class to handle the SAXParseExceptions.
 */
class AJNErrorHandler implements ErrorHandler {
    public boolean allowEmpty;
    private LogOutput UIOutput;

    public AJNErrorHandler(CodeGenConfig config, LogOutput output){
        allowEmpty = config.allowEmpty;
	UIOutput = output;
    }

    public void error(SAXParseException e) {
        if(e.getMessage().contains("The content of element 'node'") ||
           e.getMessage().contains("The content of element 'interface'")){
            if(allowEmpty){
                warning(e);        		
            }else{
                fatalError(e);
            }
        }else if(e.getMessage().contains("CodeGen warning:")){
            warning(e);

        }else{
            fatalError(e);
        }
    }

    public void fatalError(SAXParseException e) {
	String lineCol = "";
    	if(e.getLineNumber() != -1){
            lineCol = "error while parsing XML on line #"
                               + e.getLineNumber()
                               + ", column #"
                               + e.getColumnNumber()
                               + ". ";
    	}
        UIOutput.LogFatal(lineCol + e.getMessage(), 0);
    }

    public void warning(SAXParseException e) {
        String lineCol = "";
        if(e.getLineNumber() != -1){
            lineCol = "error while parsing XML on line #"
                               + e.getLineNumber()
                               + ", column #"
                               + e.getColumnNumber()
                               + ". ";
    	}
        UIOutput.LogWarning(lineCol + e.getMessage());
    }

} // class AJNErrorHandler
