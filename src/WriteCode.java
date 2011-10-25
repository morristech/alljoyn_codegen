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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;

import org.xml.sax.SAXParseException;


/**
 * Class for writing C++ template code to file This class should not be
 * instantiated directly, use the two children classes instead.  The CodeWriter
 * class contains helper methods used for creating both client and service
 * files. Methods that begin with "write" writes code to the files, methods
 * that begin with "generate" returns a string that is used by the "write"
 * methods.
 * 
 */
public class WriteCode {
    protected String objName;
    protected String fileName;
    protected static CodeGenConfig config;
    protected static ObjectData objectData;
    protected static InterfaceDescription inter;
    protected static ArrayList<ArgDef> structArgList;
    protected static ArrayList<ArgDef> printedStructList;
    protected static boolean structArgListPopulated;
    protected static ArrayList<ArgDef> dictEntryArgList;
    protected static ArrayList<ArgDef> printedDictList;
    protected static boolean dictEntryArgListPopulated;
    protected static ArrayList<ArgDef> arrayContainerTypeArgList;
    protected static boolean arrayContainerTypeArgListPopulated;
    protected static String indentDepth = FormatCode.indent(1);
    protected static String topBracket = "/*-----------------------------------------------------------------------------\n";
    protected static String bottomBracket = "-----------------------------------------------------------------------------*/\n";
    protected static String structDefs  = "";
    protected FileWriter fWriter;
    protected BufferedWriter fileOutput;
        
    protected static LogOutput UIOutput;
    
    /**
     * CodeWriter constructor.
     */
    public WriteCode() {
        printedStructList = new ArrayList<ArgDef>();
        printedDictList = new ArrayList<ArgDef>();
        structArgListPopulated = false;
        UIOutput = new LogOutput("WriteCode");
    }  
    
    /**
     * Create and write the BusAttachmentMgr.h file to the cmd line location.
     * Does nothing if the file already exists.
     * @param fileType: the type of file to create: either ".h" or ".cc"
     */
    public void writeBusMgrFile(String fileType){
        InputStream s =
            this.getClass().getResourceAsStream("src/c++/BusAttachmentMgr" +
                                                fileType);
        File f = new File(config.outputPath + "BusAttachmentMgr" + fileType);
        try {
            if(f.exists()){
                UIOutput.LogWarning("BusAttachmentMgr" +
                    fileType + " already exists.");
            }
            else{
                FileOutputStream fs = new FileOutputStream(f);
                byte[] buffer = new byte[1024];

                //insert the license text
                int len = s.read(buffer,0, 200);
                String withLicense = new String(buffer,
                                            0,
                                            len).replace("@License@",
                                                         config.cLicense);
                fs.write(withLicense.getBytes());

                // get and copy the rest of the text.
                len = s.read(buffer);
                while(len > 0){
                    fs.write(buffer, 0, len);
                    len = s.read(buffer);
                }
			
                fs.close();
                s.close();
            }
        }catch (Exception e) {
            UIOutput.LogError(e.getMessage());
            e.printStackTrace();
            UIOutput.LogFatal(
                "could not write BusAttachmentMgr" + fileType, 0);
        }
    } // writeBusMgrFile()

    /**
     * Writes the sample makefile.
     */
    public void writeMakeFile(){
    	InputStream s = this.getClass().getResourceAsStream("src/c++/makefile");
    	File f = new File(config.outputPath + "makefile");
    	try {
            if(f.exists()){
                UIOutput.LogWarning("makefile already exists.");
            }
            else{
                FileOutputStream fs = new FileOutputStream(f);
                CodeGenConfig config = CodeGenConfig.getInstance();
                fs.write(config.shLicense.getBytes());
                String variables = "# AllJoyn variables: the class name, an "
                    + "indicator of whether ther service code\n# was generated, "
                    + "and the error message if AllJoyn path isn't defined."
                    + "\n";
                if(!config.clientOnly){
                    variables += "SERVICE_GENERATED = true\n";
                }
                variables += "SVCOBJS = BusAttachmentMgr.o ServiceMain.o ";
                for(InterfaceDescription tempInter : config.interfaces){ 
                    String className = "";
                    if(ParseAJXML.useFullNames) {
                        className = tempInter.className;
                    }
                    else {
                        className = tempInter.getName();
                    }
                    variables += className + "Service.o "
                        + className + "ServiceMethods.o ";
                }
                variables += "\n";

                variables += "CLTOBJS = BusAttachmentMgr.o ClientMain.o ";
                for(InterfaceDescription tempInter : config.interfaces){
                   String className = "";
                    if(ParseAJXML.useFullNames) {
                        className = tempInter.className;
                    }
                    else {
                        className = tempInter.getName();
                    }
                    variables += className + "Client.o "
                        + className + "ClientHandlers.o ";
                }
                variables += "\n";

                fs.write(variables.getBytes());
				
                byte[] buffer = new byte[256];
                int len = s.read(buffer);
                while(len > 0){
                    fs.write(buffer, 0, len);
                    len = s.read(buffer);
                }
			
                fs.close();
                s.close();
            }
        }catch (Exception e) {
            UIOutput.LogError(e.getMessage());
            e.printStackTrace();
            UIOutput.LogFatal("could not write makefile.", 0);
        }
    }  // writeMakeFile()

    /**
     * strip the leading " * " from the cLicense string
     *
     * @return a string containing the license text without the leading " * "
     */
    public String licenseTextOnly() {
        return config.cLicense.replaceAll(" \\* ", "") + "\n";
    } // licenseTextOnly()

    /**
     * Open output file
     * 
     * @return true for success, false for failure
     */
    protected boolean openFile() {
        try {
            File f = new File(config.outputPath + fileName);
            //create a File object doesn't create the file in the filesystem
            if(f.exists()){
                if(!config.overWrite){
                    UIOutput.LogError( config.outputPath
                                       + fileName
                                       + " already exists, choose to overwrite"
                                       + " or pick a new file name.");
                    return false;        			
                }else if(fileName.contains("Methods") ||
                         fileName.contains("Handlers") ||
                         fileName.contains("Main")){

                    String temp = fileName.replace(".cc", "Copy.cc");
                    UIOutput.LogWarning("creating " +
                                       temp +
                                       " instead of overwriting " +
                                       fileName);
                    f = new File(config.outputPath + temp);
                }//if overwrite is true, overwrite non-dev files
            }
            //the FileWriter creates the file in the filesystem
            fWriter = new FileWriter(f, false);
            fileOutput = new BufferedWriter(fWriter);        		
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    } // openFile()

    /**
     * Close output file
     * 
     * @return true for success, false for failure
     */
    protected boolean closeFile() {
        try {
            fileOutput.close();
        } catch (Exception e) {
            UIOutput.LogError("COULD NOT CLOSE FILE, EXITING");
            return false;
        }
        return true;
    } // closeFile()

    /**
     * Write string to file
     * 
     * @param data
     *            the generic data to write to file
     */
    protected void writeCode(String data) {
        try {
            fileOutput.write(data);
        } catch (Exception e) {
            UIOutput.LogFatal(e.toString(),1);
        }
    } // writeCode()

    /**
     * Writes the code for header file include statements, which are different
     * depending on if the file is a service, or is a main file.
     * @param isService
     * @param isMain
     */
    protected void writeHeaderIncludes(boolean isService, boolean isMain) {
    	
    	String includes = "";
        includes += "#include <qcc/platform.h>\n";
        includes += "#include <stdio.h>\n"
            + "#include <qcc/String.h>\n";

        // change the includes depending on if it's a main file or not
        if(isMain){
            includes += "#include <signal.h>\n"
                + "#include <alljoyn/version.h>\n";
                for(InterfaceDescription tempInter : config.interfaces) {
                    String className = "";
                    if(ParseAJXML.useFullNames) {
                        className = tempInter.className;
                    }
                    else {
                        className = tempInter.getName();
                    }
                    if(isService) {
                        includes += "#include \"" + className + "Service.h\"\n";
                    }
                    else {
                        includes += "#include \"" + className + "Client.h\"\n";
                    }
                }
            if(isService){
                includes += "#include <alljoyn/DBusStd.h>\n";
            }else{
            	includes += "#include <alljoyn/AllJoynStd.h>\n";
            }
        }else{
            includes += "#include <alljoyn/BusAttachment.h>\n"
                + "#include <alljoyn/MsgArg.h>\n"
                + "#include <alljoyn/InterfaceDescription.h>\n";
        }
        
        // include the correct bus object header for service vs client
        if(isService){
            includes += "#include <alljoyn/BusObject.h>\n"
                + "#include <alljoyn/DBusStd.h>\n"
                + "#include \"BusAttachmentMgr.h\"\n";
        }else{
        	includes += "#include \"BusAttachmentMgr.h\"\n";
            includes += "#include <alljoyn/ProxyBusObject.h>\n";
        }

        includes += "#include <Status.h>\n"
            + "\n\nusing namespace ajn;\n"
            + "using namespace qcc;\n"
            + "using namespace std;\n\n";

        writeCode(includes);
    } // writeHeaderIncludes()
    
    /**
     * Writes the include statement for the CC files.
     */
    protected void writeCCIncludes() {
    	String output = "#include \""
            + objName
            + ".h\"\n"
            + "#include <alljoyn/AllJoynStd.h>\n\n"
            + "using namespace ajn;\n"
            + "using namespace qcc;\n"
            + "using namespace std;\n\n";
    	writeCode(output);
    } // writeCCIncludes()

    /**
     * Writes the alljoyn code for adding members to the interface.
     * 
     * example:
     * AddMethod(...);
     * AddSignal(...);
     * AddProperty(...);
     * 
     * @param varName the name of the alljoyn interfaceDescription variable
     * name.
     * @param iface the interface to write the add member code for.
     */
    protected void writeInterfaceMember(String varName,
                                        InterfaceDescription iface) {
    	MethodDef tempMethod;
    	SignalDef tempSignal;
    	PropertyDef tempProp;
    	String inArgs, outArgs;
    	String access;
    	String flags;
        String output = "";
        
        for(int i = 0; i < iface.methods.size(); i++){
            tempMethod = iface.methods.get(i);
            inArgs = tempMethod.getParamNames();
            outArgs = tempMethod.getRetNames();
            output += indentDepth
                + "/* add the "
                + tempMethod.getName()
                + " method to the interface */\n"
                + indentDepth
                + indentDepth;
            output += "status = "
                + varName
                + "->AddMethod(\""
                + tempMethod.getName()
                + "\", ";
        	
            if(tempMethod.noReply){
                flags = "MEMBER_ANNOTATE_NO_REPLY";
            }else{
                flags = "0";
            }

            if (tempMethod.getParams().equals("NULL")) {
                output += tempMethod.getParams() + ", ";
            } else {
                output += "\"" + tempMethod.getParams() + "\", ";
            }

            if (tempMethod.getRetType().equals("NULL")) {
                output += tempMethod.getRetType() + ", ";
            } else {
                output += "\"" + tempMethod.getRetType() + "\", ";
            }
        	
            if(inArgs.equals("NULL") && outArgs.equals("NULL")){
                output += "NULL, 0);\n" + indentDepth;
            }else if(inArgs.equals("NULL") && !outArgs.equals("NULL")){
                output += "\""
                    + outArgs
                    + "\" , "
                    + flags
                    + ");\n"
                    + indentDepth;

            }else if(!inArgs.equals("NULL") && outArgs.equals("NULL")){
                output += "\""
                    + inArgs
                    + "\" , "
                    + flags
                    + ");\n"
                    + indentDepth;
            }else{
                output += "\""
                    + inArgs
                    + ","
                    + outArgs
                    + "\" , 0);\n"
                    + indentDepth;
            }
        }

        for(int i = 0; i < iface.signals.size(); i++){
            tempSignal = iface.signals.get(i);
            output += "/* add the "
                + tempSignal.getName()
                + " signal to the interface */\n"
                + indentDepth;
            output += "status = "
                + varName
                + "->AddSignal(\""
                + tempSignal.getName()
                + "\", ";
        	
            if (tempSignal.getParams().equals("NULL")) {
                output += tempSignal.getParams() + ", ";
            } else {
                output += "\"" + tempSignal.getParams() + "\", ";
            }
        	
            if(tempSignal.getParamNames().equals("NULL")){
                output += tempSignal.getParamNames()
                    + " , 0);\n"
                    + indentDepth;
            }else{
                output += "\""
                    + tempSignal.getParamNames()
                    + "\" , 0);\n"
                    + indentDepth;
            }        		
        }
        
        for(int i = 0; i < iface.properties.size(); i++){
            tempProp = iface.properties.get(i);
            output += "/* add the "
                + tempProp.getName()
                + " property to the interface */\n"
                + indentDepth;
            output += "status = "
                + varName
                + "->AddProperty(\""
                + tempProp.getName()
                + "\", \""
                + tempProp.getSignature()
                + "\", PROP_ACCESS_";
        	
            access = tempProp.getAccess();
            if(access.equals("readwrite")){
                output += "RW";
            }else if (access.equals("read")){
                output += "READ";
            }else if(access.equals("write")){
                output += "WRITE";
            }else{
                //should not reach this case, taken care of in schema
            }//convert the access type to RW READ WRITE
        	
            output += ");\n" + indentDepth;
        }
        writeCode(output);
    } // writeInterfaceMember()


    /**
     * Maps the signature char to the C++ type;
     * @param input the signature char
     * @return String with the C++ type
     */
    protected static String mapType(char input) {
    	String type;
    	
    	switch(input){
    	
    	case 'y':
            type = "unsigned char";
            break;
    	case 'b':
            type = "bool";
            break;
    	case 'n':
            type = "short";
            break;
    	case 'q':
            type = "unsigned short";
            break;
    	case 'i':
            type = "int";
            break;
    	case 'u':
            type = "unsigned int";
            break;
    	case 'x':
            type = "long long";
            break;
    	case 't':
            type = "unsigned long long";
            break;
    	case 'v':
            type = "MsgArg";
            break;
    	case 'd':
            type = "double";
            break;
    	case 'o':
            type = "String";
            break;
    	case 'g':
            type = "String";
            break;
    	case 's':
            type = "String";
            break;
    	default:
            type = "nonBasic";    		
    	}
    	return type;
    } // mapType()
    
    /**
     * Maps a char to the alljoyn typeId
     * @param input - character to be mapped
     * @return the corresponding typeId
     */
    protected String mapALLJOYNtypeId(char input) {
    	String type;
    	
    	switch(input){
    	
    	case 'y':
            type = "ALLJOYN_BYTE";
            break;
    	case 'b':
            type = "ALLJOYN_BOOLEAN";
            break;
    	case 'n':
            type = "ALLJOYN_INT16";
            break;
    	case 'q':
            type = "ALLJOYN_UINT16";
            break;
    	case 'i':
            type = "ALLJOYN_INT32";
            break;
    	case 'u':
            type = "ALLJOYN_UINT32";
            break;
    	case 'x':
            type = "ALLJOYN_INT64";
            break;
    	case 't':
            type = "ALLJOYN_UINT64";
            break;
    	case 'd':
            type = "ALLJOYN_DOUBLE";
            break;
    	case 'o':
            type = "ALLJOYN_OBJECT_PATH";
            break;
    	case 'g':
            type = "ALLJOYN_SIGNATURE";
            break;
    	case 's':
            type = "ALLJOYN_STRING";
            break;
    	case 'v':
            type = "ALLJOYN_VARIANT";
    	default:
            type = "void";
    	}
    	return type;
    } // mapALLJOYNtypeId()
    
    /**
     * Maps the signature char to the correct C++ type with an '&' for outputs.
     * @param input the signature char
     * @return String with the C++ type with a '&' if appropriate
     */
    protected String mapOutType(char input) {
    	String type;
    	
    	switch(input){
    	
    	case 'y':
            type = "unsigned char&";
            break;
    	case 'b':
            type = "bool&";
            break;
    	case 'n':
            type = "short&";
            break;
    	case 'q':
            type = "unsigned short&";
            break;
    	case 'i':
            type = "int&";
            break;
    	case 'u':
            type = "unsigned int&";
            break;
    	case 'x':
            type = "long long&";
            break;
    	case 't':
            type = "unsigned long long&";
            break;
    	case 'v':
            type = "MsgArg&";
            break;
    	case 'd':
            type = "double&";
            break;
    	case 'o':
            type = "String&";
            break;
    	case 'g':
            type = "String&";
            break;
    	case 's':
            type = "String&";
            break;
    	default:
            type = "nonBasic";
    	}
    	return type;
    } // mapOutType()
    
    /**
     * Maps the signature char to the AllJoyn MsgArg type;
     * @param input the signature char
     * @return String with the corresponding MsgArg type
     */
    protected String mapALLJOYNMsgType(char input) {
    	String type;
    	
    	switch(input){
    	
    	case 'y':
            type = "v_byte";
            break;
    	case 'b':
            type = "v_bool";
            break;
    	case 'n':
            type = "v_int16";
            break;
    	case 'q':
            type = "v_uint16";
            break;
    	case 'i':
            type = "v_int32";
            break;
    	case 'u':
            type = "v_uint32";
            break;
    	case 'x':
            type = "v_int64";
            break;
    	case 't':
            type = "v_uint64";
            break;
    	case 'd':
            type = "v_double";
            break;
    	case 'v':
            type = "v_variant";
            break;
    	case 'o':
            type = "v_objPath.str";
            break;
    	case 'g':
            type = "v_signature.sig";
            break;
    	case 's':
            type = "v_string.str";
            break;
    	default:
            type = "void";
    	}
    	return type;
    } // mapALLJOYNMsgType()
    
    /**
     * Maps the signature char after the 'a' of an alljoyn array to the
     * corresponding AllJoyn Array type;
     * @param input the signature char that follows the 'a'
     * @return String with the corresponding Array type
     */
    protected String mapALLJOYNArrayType(char input) {
    	String type;
    	
    	switch(input){

    	case 'b':
            type = "ALLJOYN_BOOLEAN_ARRAY";
            break;
    	case 'n':
            type = "ALLJOYN_INT16_ARRAY";
            break;
    	case 'q':
            type = "ALLJOYN_UINT16_ARRAY";
            break;
    	case 'i':
            type = "ALLJOYN_INT32_ARRAY";
            break;
    	case 'u':
            type = "ALLJOYN_UINT32_ARRAY";
            break;
    	case 'x':
            type = "ALLJOYN_INT64_ARRAY";
            break;
    	case 't':
            type = "ALLJOYN_UINT64_ARRAY";
            break;
    	case 'd':
            type = "ALLJOYN_DOUBLE_ARRAY";
            break;
    	case 'y':
            type = "ALLJOYN_BYTE_ARRAY";
            break;
    	default:
            type = "ALLJOYN_ARRAY";
    	}
    	return type;
    } // mapALLJOYNArrayType()
    
    /**
     * Creates a String containing list of arguments used for method
     * declarations.
     * 
     * example:
     * "int arg1, double arg2, arg3Struct arg3, int& output1, int& output2"
     * 
     * @param argList
     * @return String with the arguments.
     */
    protected String generateArgs(ArrayList<ArgDef> argList) {
    	String output = "";
    	ArgDef tempArg;
    	
    	for(int i = 0; i < argList.size(); i++){
            tempArg = argList.get(i);
            if(tempArg.getArgDirection().equals("in")){
                output += parseInArg(tempArg);
            }else if(tempArg.getArgDirection().equals("out")){
                output += parseOutArg(tempArg);
            }
            if(i != argList.size() - 1){
                output += ", ";
            }
    	}
    	return output;
    } // generateArgs()
    
    /**
     * Creates a String containing the alljoyn code for declaring property
     * variables in the service class definition.
     * 
     * example:
     * int prop1;
     * double prop2;
     * 
     * @param propList list of properties, could be empty
     * @return the String with the property variable declarations
     */
    protected String generateProperties(ArrayList<PropertyDef> propList, int indentDepth) {
    	String output = "";
    	PropertyDef prop;
    	ArgDef tempArg = new ArgDef(null, null, null, null);
    	String commentStr;
    	for(int i = 0; i < propList.size(); i++){
            prop = propList.get(i);
            tempArg.argName = prop.getName();
            tempArg.argType = prop.getSignature();
            commentStr = String.format("MEMBER: %1$s\n" +
                "The variable that stores the \"%1$s\" property specified by " +
                "the interfaces.",
                tempArg.argName);
            output += FormatCode.blockComment(commentStr,indentDepth);
            String tempStr;
            tempStr = parseInArg(tempArg);
            tempStr = tempStr.replace(", ", ";\n" + FormatCode.indent(indentDepth));
            tempStr = tempStr.replace("const ", ""); //remove the const modifier.
            output += FormatCode.indent(indentDepth) + tempStr + ";\n";
    	}

    	return output;
    } // generateProperties()
    
    /**
     * Parses the output argument's type into C++ syntax.
     * @param arg the argument whose type is being generated.
     * @return result the argument's type and the argument name in C++ syntax
     * with a '&'
     */
    protected String parseOutArg(ArgDef arg) {
    	String result = "";
    	String type = arg.getArgType();
        char c = type.charAt(0);
        if(isArgContainerType(arg)){
            if(c == '('){
                result += arg.getArgName() + "Struct& " + arg.getArgName();
            }else if(c == 'a'){
                if(type.charAt(1) == '{' &&
                   dictEntryArgList.contains(arg)){
                    result += arg.getArgName() + "DictEntry*& " + arg.getArgName();
                    result += ", size_t& "
                        + arg.getArgName()
                        + "NumElements";
                }else if(type.charAt(1) == '(' && 
                         arrayContainerTypeArgList.contains(arg)){
                    result += arg.getArgName() + "Struct*& " + arg.getArgName();
                    result += ", size_t& "
                        + arg.getArgName()
                        + "NumElements";
                }else if(type.charAt(1) == 'a'){
                    result += "MsgArg& " + arg.getArgName();
                }else {
                    result += mapType(type.charAt(1))
                        + "*& "
                        + arg.getArgName(); 
                    result += ", size_t& "
                        + arg.getArgName()
                        + "NumElements";
                }
            }else {
                result += "MsgArg& " + arg.getArgName();
            }
        }else{
            result += mapOutType(c) + " " + arg.getArgName();
        }
    	return result;
    } // parseOutArg()

    /**
     * Parses the argument's type into C++ syntax.
     * @param arg the argument whose type is being generated.
     * @return result the argument's type and the argument name in C++ syntax.
     */
    protected static String parseInArg(ArgDef arg) {
    	String result = "";
    	String type = arg.getArgType();
        char c = type.charAt(0);
        if(isArgContainerType(arg)){
            if(c == '('){
                result += arg.getArgName() + "Struct " + arg.getArgName();
            } else if(c == 'a'){
                if(type.charAt(1) == '{' && dictEntryArgList.contains(arg)){
                    result += arg.getArgName()
        	        + "DictEntry *"
        	        + arg.getArgName();
                    result += ", size_t " + arg.getArgName() + "NumElements";
                }else if(type.charAt(1) == 'a'){
                    result += "MsgArg " + arg.getArgName();
                }else if(type.charAt(1) == '('
                         && arrayContainerTypeArgList.contains(arg)){
                    result += "const " 
                        + arg.getArgName() 
                        + "Struct *" 
                        + arg.getArgName();
                    result += ", size_t " + arg.getArgName() + "NumElements";
                }else{
                    result += "const "
                        + mapType(type.charAt(1))
                        + " *"
                        + arg.getArgName();
                    result += ", size_t " + arg.getArgName() + "NumElements";
                }
            } else {
            	result += "MsgArg " + arg.getArgName();
            }
        } else {
            result += mapType(c) + " " + arg.getArgName();
        }
		
    	return result;
    } // parseInArg()

    /**
     * Generate a string that declares an argument (ex. int x)
     * @param arg - the argument to generate code for
     * @return string containing code that declares the argument.
     */
    protected static String generateTempArg(ArgDef arg, int indentDepth){
    	String result = "";
    	String code;
    	String type = arg.getArgType();
        char c = type.charAt(0);
        if(isArgContainerType(arg)){
            if(c == 'a'){
                if(type.charAt(1) == '{' &&
                   dictEntryArgList.contains(arg)){
                    code = String.format(
                        "%1$sDictEntry* %1$s;",
                        arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                    code = String.format("size_t %sNumElements;", 
                                         arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                }else if(type.charAt(1) == '(' &&
                         arrayContainerTypeArgList.contains(arg)){
                    code = String.format("%1$sStruct *%1$s;",
                                         arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                    code = String.format("size_t %sNumElements;", 
                                         arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                }else if(isBasicArrayContainerType(type)){
                    code = String.format(
                        "%s* %s;",
                        mapType(arg.getArgType().charAt(1)),
                        arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                    code = String.format("size_t %sNumElements;", 
                                         arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                } else if(type.charAt(1) == 'a'){
                    code = String.format("MsgArg %s;",
                                         arg.getArgName());
                    result += FormatCode.indentln(code, indentDepth);
                }
                
            }else if(c == '('){
                code  = String.format("%1$sStruct %1$s;", arg.getArgName());
                result += FormatCode.indentln(code, indentDepth);
            } else {
                code = String.format("MsgArg %s;", arg.getArgName());
                result += FormatCode.indentln(code, indentDepth);
            }
        }else if(type.length() > 1){
            
        }else{
            code = String.format("%s %s;", mapType(c), arg.getArgName());
            result += FormatCode.indentln(code, indentDepth);
        }

    	return result;
    } // generateTempArg()
  
    /**
     * This will produce code that prints out each argument of the struct so it
     * can be used by a arg.Get statment.
     * 
     * @param varName the name used to access the varaible.
     * @param structSignature the full signature found inside a struct 
     * @return 
     */
    protected static String generateCommaSeparatedArgsFromStruct(
        String varName,
        String structSignature,
        boolean isGet ) {

    	String output = "";
    	int structCount = 0;
    	int memberCount = 0;
    	for(int i = 1; i < structSignature.length()-1; i++){
            char c = structSignature.charAt(i);
            switch(c){
            case '(' : {
                String innerStructSig =
                    WriteCode.innerStructSignature(
                        structSignature.substring(i,
                                                  structSignature.length()-1));
                output += generateCommaSeparatedArgsFromStruct(varName
                                                               + ".s"
                                                               + structCount, 
                                                               innerStructSig,
                                                               isGet);
                i += innerStructSig.length()-1;
                structCount++;
                break;
            }
            case ')':
                //Do nothing
                break;
            case 'a' :{
                break;
            }	
            default:{
                switch(c){
    	        case 'g': //fall through SIGNATURE type
    	        case 'o': //fall through OBJECT PATH type
    	        case 's': { //STRING type
    	            if(isGet){
    	                output += String.format(", &%s.member%d", 
    	                                        varName,
    	                                        memberCount);
    	               
    	            } else {
    	                output += String.format(", %s.member%d.c_str()", 
                                                varName,
                                                memberCount);
    	            }
    	            break;
    	        }
    	        case 'd': //fall through DOUBLE type
    	        case 't': //fall through UINT_64 type
    	        case 'x': //fall through INT_64 type
    	        case 'b': //fall through BOOLEAN type
    	        case 'i': //fall through INT_32 type
    	        case 'n': //fall through INT_16 type
    	        case 'q': //fall through UINT_16 type
    	        case 'u': //fall through UINT_32 type
    	        case 'y': //fall through BITE type
    	        default: {
    	            if(isGet){
                        output += String.format(", &%s.member%d", 
                                                varName,
                                                memberCount);
                       
                    } else {
                        output += String.format(", %s.member%d", 
                                                varName,
                                                memberCount);
                    }
                    break;
    	        }
    	        }
                memberCount++;
            }
            }
    		
    	}
    	return output;
    } // generateCommaSeparatedArgsFromStruct()
    
    protected void writeStructs(){
    	//if structDefs is empty, call lookForStruct
        if(structDefs.equals("")){
            structDefs = lookForStruct();
        }
        writeCode(structDefs);
    }

    /**
     * Creates a String containing struct definitions if there are any 
     * structs in any of the arguments in the XML file.
     * 
     * example:
     * typedef arg1Struct{
     * 		int member0;
     * 		double member1;
     * }
     * 
     * @return String with the struct typedef's
     */
    protected static String lookForStruct() {
        String output = "";
        populateStructArgList();
        for(int i = 0; i < structArgList.size(); i++){
            if(!printedStructList.contains(structArgList.get(i))) {
                isStructNameTaken(structArgList.get(i));
                output += generateStructs(structArgList.get(i).argType,
                                          structArgList.get(i).argName);
                printedStructList.add(structArgList.get(i));
            }
        }
        
        populateDictArgList();
        for(int i = 0; i < dictEntryArgList.size(); i++){
            if(!printedDictList.contains(dictEntryArgList.get(i))) {
                isDictNameTaken(dictEntryArgList.get(i));
                output += generateDictStructs(dictEntryArgList.get(i).argType,
                                              dictEntryArgList.get(i).argName);
                printedDictList.add(dictEntryArgList.get(i));
           }
        }
        populateArrayContainerTypeArgList();
        for(int i = 0; i < arrayContainerTypeArgList.size(); i++){
            output += generateArrayContainerStructs(
                arrayContainerTypeArgList.get(i));
        }
        return output;
    } // lookForStruct()

    public static String getInterfaceBySignal(String signalName) {
        if(!inter.isDerived) {
            return inter.getFullName();
        }

        for(InterfaceDescription parent : inter.parents) {
            for(SignalDef signal : parent.signals) {
                if(signal.getName().equals(signalName)) {
                    return parent.getFullName();
                }
            }
        }

        return "";
    }


    /**
     * If the given struct name already exists, then print an error and exit.
     */
    private static void isStructNameTaken(ArgDef struct)
    {
        for(ArgDef other : structArgList)
        {
            checkName(other.getArgName(), other.getArgType(), struct.getArgName(), struct.getArgType());
        }
        for(ArgDef other : printedStructList)
        {
            checkName(other.getArgName(), other.getArgType(), struct.getArgName(), struct.getArgType());
        }
    }

    /**
     * If the given dict name already exists, then print an error and exit.
     */
    private static void isDictNameTaken(ArgDef dict)
    {
        for(ArgDef other : dictEntryArgList)
        {
            checkName(other.getArgName(), other.getArgType(), dict.getArgName(), dict.getArgType());
        }
        for(ArgDef other : printedDictList)
        {
            checkName(other.getArgName(), other.getArgType(), dict.getArgName(), dict.getArgType());
        }
    }

    private static void checkName(String nameOne, String typeOne, String nameTwo, String typeTwo)
    {
        if(nameOne.equals(nameTwo) && !typeOne.equals(typeTwo))
        {
            UIOutput.LogFatal("Multiple containers with name '" + nameOne + "'.", 0);
        }
    }

    
    /**
     * Check all method, properties, and signals for the existence of a 
     * signature specifying an AllJoyn STRUCT type.  Place that ArgDef object 
     * into an ArrayList 'structArgList' that contains all of the AllJoyn 
     * STRUCTS.  
     */
    private static void populateStructArgList() {
    	if (structArgListPopulated == true){
            return;
    	}
    	structArgList = new ArrayList<ArgDef>();
    	
    	ArrayList<MethodDef> meths;
        ArrayList<PropertyDef> props;
    	ArrayList<SignalDef> sigs;
        
    	MethodDef meth;
    	PropertyDef prop;
    	SignalDef sig;
        
            meths = inter.methods;
            props = inter.properties;
            sigs = inter.signals;
            //check all methods for the existence of a struct
            for(int j = 0; j < meths.size(); j++){
                meth = meths.get(j);
                for(int k = 0; k < meth.argList.size(); k++){
                    if(hasStruct(meth.argList.get(k).getArgType())){
                        //If the struct is already in the list, do not add it again
                        if(!structArgList.contains(meth.argList.get(k))) {
                            isStructNameTaken(meth.argList.get(k));
                            structArgList.add(meth.argList.get(k));
                        }
                    }
                }
            }
            //check all properties for the existence of a struct
            for(int j = 0; j < props.size(); j++){
            	prop = props.get(j);
            	if(hasStruct(prop.getSignature())){
                    if(!structArgList.contains(prop.getArg())) {
                        isStructNameTaken(prop.getArg());
                        structArgList.add(prop.getArg());
                    }
            	}
            }
            
            //check all signals for the existence of a struct
            for(int j = 0; j < sigs.size(); j++){
            	sig = sigs.get(j);
            	for(int k = 0; k < sig.argList.size(); k++){
                    if(hasStruct(sig.argList.get(k).getArgType())){
                        if(!structArgList.contains(sig.argList.get(k))) {
                            isStructNameTaken(sig.argList.get(k));
                            structArgList.add(sig.argList.get(k));
                        }
                    }
            	}
            }
    	structArgListPopulated = true;
    } // populateStructArgList()
    
    /**
     * Check all method, properties, and signals for the existence of a 
     * signature specifying an AllJoyn DICT type.  Place that ArgDef object 
     * into an ArrayList 'dictArgList' that contains all of the AllJoyn 
     * DICTS.
     * 
     * At this point this only looks at objects that are DICT type it will not
     * find a DICT type nested inside another container.
     */
    private static void populateDictArgList() {
        dictEntryArgList = new ArrayList<ArgDef>();
        
        ArrayList<MethodDef> meths;
        ArrayList<PropertyDef> props;
        ArrayList<SignalDef> sigs;
        
        MethodDef meth;
        PropertyDef prop;
        SignalDef sig;     
        
            meths = inter.methods;
            props = inter.properties;
            sigs = inter.signals;
            //check all methods for the existence of a struct
            for(int j = 0; j < meths.size(); j++){
                meth = meths.get(j);
                for(int k = 0; k < meth.argList.size(); k++){
                    if(hasDictEntry(meth.argList.get(k).getArgType())){
                        if(!dictEntryArgList.contains(meth.argList.get(k))) {
                            isDictNameTaken(meth.argList.get(k));
                            dictEntryArgList.add(meth.argList.get(k));
                        }
                    }
                }
            }
            //check all properties for the existence of a struct
            for(int j = 0; j < props.size(); j++){
                prop = props.get(j);
                if(hasDictEntry(prop.getSignature())){
                    if(!dictEntryArgList.contains(prop.getArg())) {
                        isDictNameTaken(prop.getArg());
                        dictEntryArgList.add(prop.getArg());
                    }
                }
            }
            
            //check all signals for the existence of a struct
            for(int j = 0; j < sigs.size(); j++){
                sig = sigs.get(j);
                for(int k = 0; k < sig.argList.size(); k++){
                    if(hasDictEntry(sig.argList.get(k).getArgType())){
                        if(!dictEntryArgList.contains(sig.argList.get(k))) {
                            isDictNameTaken(sig.argList.get(k));
                            dictEntryArgList.add(sig.argList.get(k));
                        }
                    }
                }
            }
        dictEntryArgListPopulated = true;
    } // populateDictArgList()
    
    /**
     * Check all method, properties, and signals for the existence of a
     * signature specifying an AllJoyn ARRAY type that has an array of a
     * container type such as a struct or an dictionary entry.  Place that
     * ArgDef object into an ArrayList 'arrayContainerTypeArgList' that
     * contains all of the AllJoyn arrays with a container type as its element.
     */
    private static void populateArrayContainerTypeArgList() {
    	arrayContainerTypeArgList = new ArrayList<ArgDef>();
        
        ArrayList<MethodDef> meths;
        ArrayList<PropertyDef> props;
        ArrayList<SignalDef> sigs;
        
        MethodDef meth;
        PropertyDef prop;
        SignalDef sig;     
        
            meths = inter.methods;
            props = inter.properties;
            sigs = inter.signals;
            //check all methods for the existence of a struct
            for(int j = 0; j < meths.size(); j++){
                meth = meths.get(j);
                for(int k = 0; k < meth.argList.size(); k++){
                    if(hasArrayContainerType(meth.argList.get(k).getArgType())){
                        arrayContainerTypeArgList.add(meth.argList.get(k));
                    }
                }
            }
            //check all properties for the existence of a struct
            for(int j = 0; j < props.size(); j++){
                prop = props.get(j);
                if(hasArrayContainerType(prop.getSignature())){
                    arrayContainerTypeArgList.add(prop.getArg());
                }
            }
            
            //check all signals for the existence of a struct
            for(int j = 0; j < sigs.size(); j++){
                sig = sigs.get(j);
                for(int k = 0; k < sig.argList.size(); k++){
                    if(hasArrayContainerType(sig.argList.get(k).getArgType())){
                        arrayContainerTypeArgList.add(sig.argList.get(k));
                    }
                }
            }
        dictEntryArgListPopulated = true;

    } // populateArrayContainerTypeArgList()

    /**
     * Look at the specified signature if it is a AllJoyn STRUCT type return
     * true
     * @param signature signature we want to check if it is a struct
     * @return true if signature is a AllJoyn STRUCT
     */
    private static boolean hasStruct(String signature) {
    	if (signature.length() > 2 &&
            signature.startsWith("(") &&
            signature.endsWith(")")){
            return true;
    	}
    	return false;
    } // hasStruct()
	
    /**
     * checks to see if the argument's signature is an array of 
     * dictionary entries.  
     * a valid dictionary entry must have a signature that starts with 'a{' and
     * ends with '}'  the letter following the '{' must be a basic type or it 
     * is an invalid dictonary entry.
     * 
     * @param arg - an ArgDef to check to see if it ia an AllJoyn DICT type.
     * @return true if this is an AllJoyn DICT type.
     */
    private static boolean hasDictEntry(String signature){
        if( signature.length() > 4 && 
            signature.charAt(0) == 'a' &&
            signature.charAt(1) == '{' &&
            signature.charAt(signature.length()-1) == '}'){
            if(mapType(signature.charAt(2)).equals("nonBasic")){
                UIOutput.LogFatal(String.format(
                                       "CodeGen error: invalide DICT entry "
                                       + "found with the %s signature. The "
                                       + "key must be a basic data type not a "
                                       + "container type.",
                                       signature), 0);
            } else {
                return true;
            }
        }
        return false;
    } // hasDictEntry()

    /**
     * This will find arrays that contain a dictionary entry or a struct we must
     * generate a struct for these arrays. 
     * @param signature
     * @return true if the array contains one of the specified container types
     */
    private static boolean hasArrayContainerType(String signature){
    	if(signature.charAt(0) == 'a'){
            //this is a Dictionary entry not an array of other container types.
            if(signature.charAt(1) == '{'){
                return false;
            }
            int index = 1;
            while(index < signature.length()){
                if(signature.length() > index+2){
                    // the array contains a DICT entry
                    if(signature.charAt(index) == 'a' && 
                       signature.charAt(index+1) == '{'){
                        return true;
                        // the array contains a STRUCT entry
                    } else if(signature.charAt(index) == '('){
                        return true;
                        // array contains another array
                    } else if(signature.charAt(index) == 'a'){
                        index++;
                        continue;
                    } else {
                        //this line of code should never be reached.
                        index++;
                    }
                } else {
                    return false;
                }
            }
    	} else {
            return false;
    	}
    	return false;
    } // hasArrayContainerType()
    
    /**
     * return a line of C++ code that allocates a block of memory for type of 
     * object.
     * sample input:
     * 		allocateArrayPtr("aPtr", "inputStruct", "20", 1); 
     * sample output:
     * 		aPtr = new inputStruct[20];
     * @param varName - the variable name of the array pointer
     * @param type - the data type that is being allocated
     * @param NumElements - the number of array elements being allocated
     * @param indentDepth - the indent depth of this line of code
     * @return
     */
    private static String allocateArrayPtr(String varName,
                                           String type,
                                           String NumElements,
                                           int indentDepth) {
    	String output = "";
    	String code = String.format("%s = new %s[%s];",
                                    varName,
                                    type,
                                    NumElements);
    	output += FormatCode.indentln(code, indentDepth);
    	return output;
    } // allocateArrayPtr()

    /**
     * See generateStructs(String signature,
     *                     String name,
     *                     boolean isInnerArray,
     *                     int indentDepth)
     * @param signature
     * @param name
     * @return
     */
    private static String generateStructs(String signature, String name) {
    	return generateStructs(signature, name, 0);
    } // generateStructs()
    
    /**
     * See generateStructs(String signature,
     *                     String name,
     *                     boolean isInnerArray,
     *                     int indentDepth)
     * @param signature
     * @param name
     * @param indentDepth
     * @return
     */
    private static String generateStructs(String signature,
                                          String name,
                                          int indentDepth) {
    	return generateStructs(signature, name, false, indentDepth);
    }
    
    /**
     * This will generate the a structure with a given name from the given 
     * signature
     * This is a recursive function that handles structs within structs or 
     * passes the functonality of other continer types on to other code.
     * @param signature signature of the struct
     * @param name name of the struct
     * @param isInnerArray - if this is an array of structs inside another 
     *                       struct set this to true.
     * @param indentDepth the indentDepth of the struct being created.
     * @return
     */
    private static String generateStructs(String signature,
                                          String name,
                                          boolean isInnerArray,
                                          int indentDepth){
    	String output = "";
    	String code;
    	int innerStructCount = 0;
    	int innerDictCount = 0;
    	int memberCount = 0;
    	if (indentDepth > 32){
            UIOutput.LogFatal("The maximum struct depth of "
                               + "32 has been exceeded", 0);
    	}
    	if (indentDepth == 0){
            output += FormatCode.blockComment(name
                                              + ": struct that is used as "
                                              + "part of the client class.");
    	}
    	output += FormatCode.indent(indentDepth) + "struct " + name
            + "Struct{\n";
    	for(int i=1; i < signature.length()-1; i++){
            String type = mapType(signature.charAt(i));
            if (type.contentEquals("nonBasic")){
                switch(signature.charAt(i)){
                case '(': {
                    String innerSig = innerStructSignature(
                        signature.substring(i, signature.length()-1));
                    output += generateStructs(innerSig,
                                              "s"+innerStructCount,
                                              indentDepth+1);
                    innerStructCount++;
                    i += innerSig.length()-1;
    	            break;
                }
                case '{':
                    // the '{' character should always be preceded by 'a'
                    UIOutput.LogError("Reached an unexpected state when "
                                       + "processing the signature \""
                                       + signature
                                       + "\".  The generated "
                                       + "code may not run as expected.");
                    break;
                case 'a':
                    if(isBasicArrayContainerType(signature.substring(i, i+2))){
                        code = String.format("%s *member%d;", 
                                             mapType(signature.charAt(i+1)),
                                             memberCount);
                        output += FormatCode.indentln(code, indentDepth+1);
                        code = String.format("size_t member%dNumElements;", 
                                             memberCount);
                        output += FormatCode.indentln(code, indentDepth+1);
                        memberCount++;
                        i++;
                    }else if(signature.charAt(i+1) == '{'){
                        String innerDict = innerDictSignature(
                            signature.substring(i));
                        output += generateDictStruct(innerDict,
                                                     "d"+innerDictCount,
                                                     indentDepth+1);
                        code = String.format("d%1$dDictEntry *d%1$d;", 
                                             innerDictCount);
                        output += FormatCode.indentln(code, indentDepth+1);
                        code = String.format("size_t d%dNumElements;", 
                                             innerDictCount);
                        output += FormatCode.indentln(code, indentDepth+1);
                        innerDictCount++;
                        i += innerDict.length()-1;
                    }else if(signature.charAt(i+1) == '('){
                        String innerSig = innerStructSignature(
                            signature.substring(i+1, signature.length()-1));
                        output += generateStructs(innerSig,
                                                  "s"+innerStructCount,
                                                  true,
                                                  indentDepth+1);
                        innerStructCount++;
                        i += innerSig.length();
        				
                    }
                    break;
                default:
                    break;
                }	
            } else {
                code = String.format("%s member%d;", 
                                     type,
                                     memberCount);
                output += FormatCode.indentln(code, indentDepth+1);
                memberCount++;
            }
        	
    	}
    	if (indentDepth == 0){
            output += "};\n";
    	} else if(isInnerArray){
            output += FormatCode.indentln("};", indentDepth);
            code = String.format("%sStruct *%s;", name, name);
            output += FormatCode.indentln(code, indentDepth);
            code = String.format("size_t %sNumElements;", name);
            output += FormatCode.indentln(code, indentDepth);
    	} else {
            output += FormatCode.indentln("}" + name +";", indentDepth);
    	}
    	innerStructCount++;
    	return output;
    } // generateStructs()

    /**
     * given a struct with an inner struct return the string representing the
     * inner struct i.e.  given the signature (i(s(is))) will return (s(is))
     * @param signature
     * @return
     */
    public static String innerStructSignature(String signature) {
    	String output ="";
    	int parenCount = 0;
    	int index = 0;
    	do{
            if(signature.charAt(index) == '('){
                parenCount++;
            }
            if(signature.charAt(index) == ')'){
                parenCount--;
            }
            index++;
    	}while((parenCount > 0) && (index < signature.length()));
    	if (index-1 < signature.length()){
            output = signature.substring(0, index);
    		
    	}
    	return output;
    } // innerStructSignature()
    
    /**
     * given a signature with an inner dictionary entry return the string 
     * representing the dictionary entry
     * i.e.  given the signature (ia{i(sa{ss})}) will return a{i(sa{ss})}
     * @param signature
     * @return
     */
    public static String innerDictSignature(String signature){
    	String output ="";
    	int parenCount = 0;
    	int index = 0;
    	int start = 0;
    	//find the start of the dictEntry
    	do{
            if(signature.charAt(index) == 'a'
               && signature.charAt(index+1) == '{'){
                parenCount++;
            }
            index++;
    	}while(parenCount == 0 && index < signature.length());
    	if(parenCount == 0){
            return "";
    	} else {
            start = index-1;
    	}
    	//find the end of the dictEntry
    	do{
            if(signature.charAt(index) == 'a'
               && signature.charAt(index+1) == '{'){
                parenCount++;
            }
            if(signature.charAt(index) == '}'){
                parenCount--;
            }
            index++;
    	}while((parenCount > 0) && (index < signature.length()));
    	if (index-1 < signature.length()){
            output = signature.substring(start, index);
    		
    	}
    	return output;
    }
    
    /**
     * Generate a struct with a key/value pair with the name indicated using 
     * the provided string.
     * 
     * @param signature - a Dictionary entry signature i.e. a{is}
     * @param name the name the struct will have.
     * @return
     */
    private static String generateDictStructs(String signature, String name) {
        return generateDictStruct(signature, name, 0);
    } // generateDictStructs()
    
    private static String generateDictStruct(String signature,
                                             String name,
                                             int indentDepth) {
        String output = "";
        String code;
        if (indentDepth == 0){
            String commentStr;
            commentStr = "DictEntry: a struct that is used as part of the " +
                "client class to represent a dictionary entry.";
            output += FormatCode.blockComment(name + "DictEntry" +commentStr);
        }
        
        code = String.format("struct %s{", 
                             name + "DictEntry");
        output += FormatCode.indentln(code, indentDepth);
        code = String.format("%s key;",
                             mapType(signature.charAt(2)));
        output += FormatCode.indentln(code, indentDepth+1);
        char c = signature.charAt(3);
        switch(c){
        case '(':{
            String innerStruct = innerStructSignature(signature.substring(3));
            output += generateStructs(innerStruct, "value", indentDepth+1);	
            break;
        }
        case 'a':{
            if(signature.charAt(4) == '{'){
                String innerDict = innerDictSignature(signature.substring(2));
                output += generateDictStruct(innerDict, "value", indentDepth+1);
                output += FormatCode.indentln("valueDictEntry *value;",
                                              indentDepth+1);
                output += FormatCode.indentln("size_t valueNumElements;",
                                              indentDepth+1);
            }else if(signature.charAt(4) == '('){
                String innerStruct = innerStructSignature
                    (signature.substring(4));
                output += generateStructs(innerStruct,
                                          "value",
                                          true,
                                          indentDepth+1);	
                break;        		
            }else{
                code = String.format("%s *value;",
                                     mapType(signature.charAt(4)));
                output += FormatCode.indentln(code, indentDepth+1);
                output += FormatCode.indentln("size_t valueNumElements;",
                                              indentDepth+1);
            }
            
            break;
        }
        default:
            output += String.format("%s%s value;\n",
                                    FormatCode.indent(indentDepth+1),
                                    mapType(signature.charAt(3)));
        }
        output += FormatCode.indentln("};", indentDepth);
        return output;
        
    } //  generateDictStruct()
    
    /**
     * parse out the container from the array and produce a struct for that
     * type of container.
     * @param arg an ArgDef that specifies an array of a container type. 
     * @return
     */
    private static String generateArrayContainerStructs(ArgDef arg){
    	String output = "";
    	String signature = arg.getArgType();
    	for (int i = 1; i < signature.length(); i++){
            if(signature.charAt(i) == '('){
                ArgDef tempArg = new ArgDef(arg.getArgName(), signature.substring(i), null, null);
                if(!printedStructList.contains(tempArg)) {
                    isStructNameTaken(tempArg);
                    output += generateStructs(signature.substring(i),
                                              arg.getArgName());
                    printedStructList.add(tempArg);
                }
                break;
            } else if(signature.charAt(i) == 'a' && 
                      signature.charAt(i+1) == '{'){
                ArgDef tempArg = new ArgDef(arg.getArgName(), signature.substring(i), null, null);
                if(!printedDictList.contains(tempArg)) {
                    isDictNameTaken(tempArg);
                    output += generateDictStructs(signature.substring(i),
                                                  arg.getArgName() + "DictEntry");
                    printedDictList.add(tempArg);
                }
                break;
            }
    	}
    	return output;
    }
    /**
     * Checks if the argument's signature a signature for a container type.
     * there are four container types: STRUCT, ARRAY, VARIANT, and DICT_ENTRY
     * @param arg
     * @return true if the signature a container type.
     */
    protected static boolean isArgContainerType(ArgDef arg) {
        boolean result = false;
        String type = arg.argType;
        result = isSignatureContainerType(type);
        return result;
    } // isArgContainerType()
    
    protected static boolean isBasicArrayContainerType(String signature) {
        if(signature.length() > 1 && signature.length() < 3){
            if(signature.charAt(0) == 'a'){
                switch(signature.charAt(1)){
                case 'b':
                case 'd':
                case 'g':
                case 'i':
                case 'n':
                case 'o':
                case 'q':
                case 's':
                case 't':
                case 'u':
                case 'x':
                case 'y': {
                    return true;
                }
                default:{
                    return false;
                }
                }
            }
        }
            
        return false;
    } // isBasicArrayContainerType()

    /**
     * Checks if the argument's signature is for a container type.
     * there are four container types: STRUCT, ARRAY, VARIANT, and DICT_ENTRY
     * A STRUCT will begin with the letter '('
     * An ARRAY and DICT_ENTRY will begin with the letter 'a'
     * a VARIANT will be the letter 'v'
     * @param signature the signature that we want to check if is is a
     * container type.
     * @return true if the signature a container type.
     */
    protected static boolean isSignatureContainerType(String signature) {
    	if(signature.length() > 1){
            if(signature.charAt(0) == '('){
            	//STRUCT containerType
            	return true;
            }else if(signature.charAt(0) == 'a'){
                //ARRAY or DICT_ENTRY
            	return true;
            }else{
                //simple type of length > 1
                //this case should not be reached, bad signature definition
                return true;
            }
        }else if(signature.charAt(0) == 'v'){
            return true;
        }
    	return false;
    } // isSignatureContainerType()

    /**
     * replace all occurrences of the char '.' with the char '_'.
     * remove the following characters '[' and ']'.
     * This is used to produce a unique variable name based on a scoped name of 
     * the same variable.
     * a string like output.member0[i] becomes --> output_member0i
     * @param var - the string we wish to replace the characters in
     * @return the resulting string with the characters replaced.
     */
    public static String replaceChars(String var){
    	String output = var.replace('.', '_');
        output = output.replace("[", "");
        output = output.replace("]", "");
        return output;
    }
    
    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * this code will handle container types as well as basic types.
     * {@code generateSetMsgArg("var", "i", "myInt",0);}
     * will produce the following 
     * {@code var.Set("i", myInt);}
     * other examples
     * {@code generateSetMsgArg("var", "((i)(i(si)))", "myStruct",0);}
     * {@code generateSetMsgArg("msg", "a{is}", "myDictStruct",0);}
     * {@code generateSetMsgArg("var", "v", "new MsgArg(\"s\", \"Hello world\")",0);"}
     * {@code generateSetMsgArg("var", "i", "106",0);}
     * {@code generateSetMsgArg("var", "s", "Hello world",0);}
     * 
     * @param msgArgName - the variable name of the MsgArg that will be Set
     * @param signature - the signature of the MsgArg
     * @param varName - the name of the variable that holds the value.  Note
     * 			the actual value could be used as the varName.
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return
     */
    public static String generateSetMsgArg(String msgArgName,
                                           String signature,
                                           String varName,
                                           int indentDepth) {
    	String output = "";
    	if(isSignatureContainerType(signature)){
            output += generateSetMsgArgContainerType(msgArgName,
                                                     signature,
                                                     varName,
                                                     indentDepth);
    	} else {
            //all basic signature types should be a single letter
            output += generateSetMsgArgBasicType(msgArgName,
                                                 signature.charAt(0),
                                                 varName,
                                                 indentDepth);	
    	}    	
    	return output;
    } // generateSetMsgArg() 
    
    /**
     * Generate the code to Set the contents of a MsgArg from a given signature.
     * This code will only work for basic data types.
     * 
     * This code assumes the data passed to it is a legal basic data type.
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the character that represents the signature of the
     * basic type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     * method
     * @return 
     */
    public static String generateSetMsgArgBasicType(String msgArgName,
                                                    char signature,
                                                    String varName,
                                                    int indentDepth) {
        String output = "";
        switch(signature){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            output += String.format("%s%s.Set(\"%c\", %s.c_str());\n", 
                                    FormatCode.indent(indentDepth),
                                    msgArgName,
                                    signature,
                                    varName);
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y':  //fall through BITE type
        
        default: {
            output += String.format("%s%s.Set(\"%c\", %s);\n", 
                                    FormatCode.indent(indentDepth),
                                    msgArgName,
                                    signature,
                                    varName);
            break;
        }
        }	
        return output;
    } // generateSetMsgArgBasicType()
    
    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * This code will only work for AllJoyn Container types. 
     * 
     * There are four container types: STRUCT, ARRAY, VARIANT, and DICT_ENTRY
     * 
     * This code assumes the signature passed to it is a legal AllJoyn
     * container type.
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a container
     * type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateSetMsgArgContainerType(String msgArgName, 
                                                        String signature, 
                                                        String varName, 
                                                        int indentDepth) {
        String output = "";
        switch(signature.charAt(0)){
        case '(': { //STRUCT

            output += generateSetMsgArgStructType(msgArgName,
                                                  signature,
                                                  varName,
                                                  indentDepth);
            break;
        }
        case 'a': {
            if (signature.charAt(1) == '{'){
                output += generateSetMsgArgDictEntryType(msgArgName,
                                                         signature,
                                                         varName,
                                                         indentDepth);
            } else if(signature.charAt(1) == 'a'){
                //Multi-dimensional arrays will be passed as MsgArgs

            	output += String.format("%s%s = %s;\n",
                                        FormatCode.indent(indentDepth),
                                        msgArgName,
                                        varName);
            } else if(signature.charAt(1) == '('){

            	output += generateSetMsgArgStructArray(msgArgName,
                                                       signature,
                                                       varName,
                                                       indentDepth);
            } else {
                output += generateSetMsgArgSimpleArrayType(msgArgName,
                                                           signature,
                                                           varName,
                                                           indentDepth);
            }
            break;
        }
        case 'v':{
            break;
        }
        default:
            //do nothing
            break;
        }
        return output;
        
    } // generateSetMsgArgContainerType()
    
    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * This code will only work for AllJoyn STRUCT Container types. 
     *  
     * This code assumes the signature passed to it is a legal AllJoyn STRUCT 
     * container type. 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     * method
     * @return 
     */
    public static String generateSetMsgArgStructType(String msgArgName, 
                                                     String signature, 
                                                     String varName, 
                                                     int indentDepth) {
        String output = "";
        output += generateStructArgsForInternalStructArrays(msgArgName,
                                                            signature,
                                                            varName,
                                                            indentDepth);
        output += generateSetMsgArgForInternalDictEntrys(msgArgName,
                                                         signature,
                                                         varName,
                                                         indentDepth);
        String listOfArgs = generateStructArgsForSetMsgArg(varName, 
                                                           signature);

        output += String.format("%s%s.Set(\"%s\"%s);\n", 
                                FormatCode.indent(indentDepth),
                                msgArgName,
                                signature,
                                listOfArgs);
        return output;
    } // generateSetMsgArgStructType()
    
    /**
     * this will accept a signature of type (a(is)) or a{sa(is)}.  The are 
     * container types with another struct or dictonary inside of them 
     */
    public static String generateStructArgsForInternalStructArrays(String msgArgName, String signature, String varName, int indentDepth){
    	String output ="";
    	int dictCount = 0;
    	int structCount = 0;
    	int memberCount = 0;
    	String code;
    	for(int i = 1; i < signature.length(); i++){
            if(signature.charAt(i) == 'a' && signature.charAt(i+1) == '('){
                String innerSignature = innerStructSignature(
                    signature.substring(i+1));
                String innerVarName = varName + ".s" + structCount;
                code = String.format("MsgArg * temp%s = new MsgArg[%sNumElements];",
                                     replaceChars(innerVarName),
                                     innerVarName);
                output += FormatCode.indentln(code, indentDepth);
                char counter = GenerateRunnableCode.getCounterVar();
                code = String.format("for(unsigned int " + counter + " = 0; " + counter + " < %sNumElements; " + counter + "++){", 
                                     innerVarName);
                output += FormatCode.indentln(code, indentDepth);
                String tempMsgArgName = "temp"
                    + replaceChars(innerVarName)
                    + "[" + counter + "]";
                output += generateSetMsgArgStructType(tempMsgArgName,
                                                      innerSignature,
                                                      innerVarName + "[" + counter + "]",
                                                      indentDepth+1);
                output += FormatCode.indentln("}", indentDepth);
                GenerateRunnableCode.endForLoop();
                i += innerSignature.length() - 1;
                structCount++;
    			
            }else if(signature.charAt(i) == 'a'
                     && signature.charAt(i+1) == '{'){
                String innerSignature = innerDictSignature(
                    signature.substring(i));
                i += innerSignature.length() - 1;
                dictCount++;
            }else if(signature.charAt(i) == 'a'
                     && (signature.charAt(i+1) == 's'
                         || signature.charAt(i+1) == 'g'
                         || signature.charAt(i+1) == 'o')){
                output += generateCharPtrArray(varName+".member" + memberCount,
                        signature.substring(i, i+2),
                        indentDepth);
                memberCount++;
                i++;
            }else if(signature.charAt(i) == '('){
                String innerVarName = varName + ".s" + structCount;
                String innerSignature = innerStructSignature(
                    signature.substring(i));
                output += generateStructArgsForInternalStructArrays(
                    msgArgName,
                    innerSignature,
                    innerVarName,
                    indentDepth);
                i += innerSignature.length() - 1;
                structCount++;
            }else{
                memberCount++;
            }
    	}
    	return output;
    }
    /**
     * this will accept a signature of type (a{is}) or a{sa{is}} and it will
     * generate an array of Set message args for the internal dictionary entry  
     * @param msgArgName
     * @param signature
     * @param varName
     * @param indentDepth
     * @return
     */
    public static String generateSetMsgArgForInternalDictEntrys(
        String msgArgName,
        String signature,
        String varName,
        int indentDepth){
    	String output ="";
    	int dictCount = 0;
    	int structCount = 0;
    	for(int i = 1; i < signature.length(); i++){
            if(signature.charAt(i) == 'a' && signature.charAt(i+1) == '{'){
                String innerSignature = innerDictSignature(
                    signature.substring(i));
                String innerVarName = varName + ".d" + dictCount;
                output += generateSetMsgArgArrayOfSingleDictEntryType(
                    msgArgName,
                    innerSignature,
                    innerVarName,
                    0,
                    indentDepth);
                i += innerSignature.length() - 1;
                dictCount++;
            }
            if(signature.charAt(i) == '('){
                String innerVarName = varName + ".s" + structCount;
                String innerSignature = innerStructSignature(
                    signature.substring(i));
                output += generateSetMsgArgForInternalDictEntrys(msgArgName,
                                                                 innerSignature,
                                                                 innerVarName,
                                                                 indentDepth);
                i += innerSignature.length() - 1;
                structCount++;
            }
    	}
    	return output;
    }
    
    /**
     * Given a varible name and a valid struct signature this will produce a 
     * comma separated list of all of the elements in the struct with the 
     * modifiers needed to make them work with the MsgArg Set command. 
     * example:
     * varName = myStruct, structSignature =((i)(id(si))) will return
     * ", myStruct.s0.member0, myStruct.s1.member0, &myStruct.s1.member1, 
     *  myStruct.s1.s0.member0.c_str(), myStruct.s1.s0.member1" 
     * 
     * Note 
     * -the first element starts with a comma qcc::Strings are changed to
     *  c strings. 
     * -large data types like doubles, int_64 and uint_64 are passed into  
     *  the set command via pointers. 
     * @param varName
     * @param structSignature
     * @return
     */
    public static String generateStructArgsForSetMsgArg(String varName,
                                                        String structSignature){
        String output = "";
        int structCount = 0;
        int dictCount = 0;
        int memberCount = 0; 
        for(int i = 1; i < structSignature.length()-1; i++){
            char c = structSignature.charAt(i);
            switch(c){
            case '(' : {
                String innerStructSig = 
                    innerStructSignature(structSignature.substring(
                                             i,
                                             structSignature.length()-1));
                output += generateStructArgsForSetMsgArg(varName
                                                         + ".s"
                                                         + structCount, 
                                                         innerStructSig);
                i += innerStructSig.length()-1;
                structCount++;
                break;
            }
            case ')':
                //Do nothing
                break;
            case 'a' :{
            	if (isBasicArrayContainerType(
                        structSignature.substring(i, i+2))){
                    output += generateArrayArgsForSetMsgArg(
                        varName + ".member"+ memberCount,
                        structSignature.substring(i, i+2));
                    memberCount++;
                    i++;
            	}else if(structSignature.charAt(i+1) == '{'){
                    output += String.format(", %s.d%dNumElements,"
                                            +" temp%s_d%dDictEntries",
                                            varName,
                                            dictCount,
                                            replaceChars(varName),
                                            dictCount);
/*
                    output += String.format(", %s.d%dNumElements,"
                                            +" %s.d%d",
                                            varName,
                                            dictCount,
                                            varName,
                                            dictCount);
*/
                    i += innerDictSignature(
                        structSignature.substring(i)).length() - 1;
                    dictCount ++;
                }else if(structSignature.charAt(i+1) == '('){
                    output += String.format(", %s.s%dNumElements, temp%s_s%d",
                                            varName,
                                            structCount,
                                            replaceChars(varName),
                                            structCount);
                    i += innerStructSignature(
                        structSignature.substring(i+1)).length() - 1;
                    structCount ++;
                }
                break;
            }  
                //it is one of the basic types if this case is reached.
            default:{
                switch(c){
                case 'g': //fall through SIGNATURE type
                case 'o': //fall through OBJECT PATH type
                case 's': { //STRING type
                    output += String.format(", %s.member%d.c_str()", 
                                            varName,
                                            memberCount);
                    break;
                }
                case 'd': //fall through DOUBLE type
                case 't': //fall through UINT_64 type
                case 'x': //fall through INT_64 type
                case 'b': //fall through BOOLEAN type
                case 'i': //fall through INT_32 type
                case 'n': //fall through INT_16 type
                case 'q': //fall through UINT_16 type
                case 'u': //fall through UINT_32 type
                case 'y': //fall through BITE type
                default: {
                    output += String.format(", %s.member%d", 
                                            varName,
                                            memberCount);
                    break;
                }
                }
                memberCount++;
                break;
            }
            }

        }
        return output;
    } // generateStructArgsForSetMsgArg()
    
    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * This code will only work for AllJoyn DICT type. with signatures
     * of type a{ii}, a{ss}, etc. 
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn DICT 
     *      container type
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this method
     * @return 
     */
    public static String generateSetMsgArgDictEntryType(String msgArgName,
                                                        String signature,
                                                        String varName,
                                                        int indentDepth){
    	return generateSetMsgArgDictEntryType(msgArgName,
                                              signature,
                                              varName,
                                              0,
                                              indentDepth);
    }
    public static String generateSetMsgArgDictEntryType(String msgArgName,
                                                        String signature,
                                                        String varName,
                                                        int loopDepth,
                                                        int indentDepth){
        String output = "";
        String code;
        output += generateSetMsgArgArrayOfSingleDictEntryType(msgArgName,
                                                              signature,
                                                              varName,
                                                              loopDepth,
                                                              indentDepth);
        code = String.format(
            "%s.Set(\"%s\", %sNumElements, temp%sDictEntries);",
            msgArgName,
            signature,
            varName,
            varName);
        output += FormatCode.indentln(code, indentDepth);
        return output;
    }
    
    /**
     * Generate the code to Set the contents of am Array of MsgArgs from a
     * given signature this code will only work for AllJoyn DICT type. with
     * signatures of type a{ii}, a{ss}, etc.
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn DICT 
     *      container type
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateSetMsgArgArrayOfSingleDictEntryType(
        String msgArgName,
        String signature,
        String varName,
        int loopDepth,
        int indentDepth){
    	String output = "";
    	String code;
    	String tempVarName = replaceChars(varName);
        code = String.format(
            "MsgArg *temp%sDictEntries = new MsgArg[%sNumElements];",
            tempVarName,
            varName);
        output += FormatCode.indentln(code, indentDepth);
        code = String.format(
            "for(unsigned int i%d = 0; i%d < %sNumElements; i%d++){",
            loopDepth,
            loopDepth,
            varName,
            loopDepth);
        output += FormatCode.indentln(code, indentDepth);
        String tempKey = "";
        switch(signature.charAt(2)){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            tempKey = String.format(", %s[i%d].key.c_str()",
                                    varName,
                                    loopDepth);
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default:{
            tempKey = String.format(", %s[i%d].key", varName, loopDepth);;
            break;
        }    
        }
        
        String tempValue="";
        switch(signature.charAt(3)){
        case 'g':
        case 'o':
        case 's':{
            tempValue = String.format(", %s[i%d].value.c_str()",
                                      varName,
                                      loopDepth);
            break;
        }
        case '(':{
            String innerStruct = innerStructSignature(signature.substring(3));
            output += generateStructArgsForInternalStructArrays(
                  msgArgName,
                  innerStruct,
                  varName +"[i" + loopDepth +"].value",
                  indentDepth+1);
            tempValue = generateStructArgsForSetMsgArg(
                varName +"[i" + loopDepth +"].value", 
                innerStruct);
            break;
        }
        case 'a':{
            if(signature.charAt(4) == '('){
                code = String.format("MsgArg *temp%s_value = new "
                                     + "MsgArg[%s[i%d].valueNumElements];",
                                     tempVarName,
                                     varName,
                                     loopDepth);
                output += FormatCode.indentln(code, indentDepth+1);
                code = String.format("for(unsigned int i%d = 0; i%d < %s[i%d]."
                                     + "valueNumElements; i%d++){",
                                     loopDepth + 1,
                                     loopDepth + 1,
                                     varName,
                                     loopDepth,
                                     loopDepth +1);
                output += FormatCode.indentln(code, indentDepth+1);
        		
                String innerStruct = innerStructSignature(
                    signature.substring(4, signature.length()-1));
                output += generateSetMsgArgStructType("temp"
                                                      + tempVarName
                                                      + "_value[i"
                                                      + (loopDepth+1)
                                                      + "]", 
                                                      innerStruct, 
                                                      varName
                                                      + "[i"
                                                      + loopDepth
                                                      + "].value[i"
                                                      + (loopDepth+1)
                                                      + "]", 
                                                      indentDepth +2 );
                output += FormatCode.indentln("}", indentDepth+1);
                tempValue = String.format(", %s[i%d].valueNumElements, "
                                          + "temp%s_value", 
                                          varName,
                                          loopDepth,
                                          tempVarName);
            }else if(signature.charAt(4) == '{'){
                String innerDict = innerDictSignature(signature.substring(3));
                output += generateSetMsgArgArrayOfSingleDictEntryType(
                    "temp"
                    + tempVarName + "DictEntries[i" + loopDepth +"]", 
                    innerDict ,
                    varName + "[i" + loopDepth + "].value",
                    loopDepth+1, 
                    indentDepth+1);
                tempValue = String.format(", %s[i%d].valueNumElements,"
                                          + " temp%si%d_valueDictEntries",
                                          varName,
                                          loopDepth,
                                          tempVarName,
                                          loopDepth);
            }else{
                switch(signature.charAt(4)){
                case 'g':
                case 'o':
                case 's':{
                	output += generateCharPtrArray(varName + "[i" + loopDepth + "]" + ".value",
                            signature.substring(3, 5),
                            indentDepth+1);
                	String tempValueVarName = String.format("temp%si%d_value",
                			tempVarName,
                			loopDepth);
                    tempValue = String.format(", %s[i%d].valueNumElements, "
                                              + tempValueVarName, 
                                              varName, 
                                              loopDepth);
                    break;
                }
                default:{
                    tempValue = String.format(", %s[i%d].valueNumElements, "
                                              + "%s[i%d].value", 
                                              varName, 
                                              loopDepth,
                                              varName, 
                                              loopDepth);
                    break;
                }
                }
            }
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default:{
            tempValue = String.format(", %s[i%d].value", varName, loopDepth);
            break;
        }
        }
        code = String.format(
            "temp%sDictEntries[i%d].Set(\"%s\"%s%s);",
            tempVarName,
            loopDepth,
            signature.substring(1),
            tempKey,
            tempValue);
        output += FormatCode.indentln(code, indentDepth+1);
        output += FormatCode.indentln("}", indentDepth);
        return output;
    } // generateSetMsgArgDictEntryType()

    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * This code will only work for single dimensional ARRAYS that containd a 
     * STRUCT type as its inner type
     * Example:  a(ii), a(is(ii)), a(di), etc. 
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn ARRAY 
     *      container type
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateSetMsgArgStructArray(String msgArgName,
                                                      String signature,
                                                      String varName,
                                                      int indentDepth){
    	String output = "";
    	String code;
    	code = String.format(
            "MsgArg *temp%1$sArg = new MsgArg[%1$sNumElements];",
            varName);
    	output += FormatCode.indentln(code, indentDepth);
        char counter = GenerateRunnableCode.getCounterVar();
    	code = String.format(
            "for(unsigned int " + counter + "=0; " + counter + " < %sNumElements; " + counter + "++){",
            varName);
    	output += FormatCode.indentln(code, indentDepth);
        /* Need another temp arg */
        output += generateStructArgsForInternalStructArrays(msgArgName,
                                                            signature.substring(1),
                                                            varName + "[" + counter + "]",
                                                            indentDepth+1);
        output += generateSetMsgArgForInternalDictEntrys(msgArgName,
                                                         signature.substring(1),
                                                         varName + "[" + counter + "]",
                                                         indentDepth+1);
        String listOfArgs = generateStructArgsForSetMsgArg(varName + "[" + counter + "]",
                                                           signature);


        /* ***** */         
    	code = String.format(    			
            "temp%sArg[" + counter + "].Set(\"%s\"%s);",
            varName,
            signature.substring(1),
            generateStructArgsForSetMsgArg(varName+"[" + counter + "]",
                                           signature.substring(1)));
    	output += FormatCode.indentln(code, indentDepth+1);
    	output += FormatCode.indentln("}", indentDepth);
        GenerateRunnableCode.endForLoop();
    	code = String.format(
            "%s.Set(\"%s\", %sNumElements, temp%sArg);",
            msgArgName,
            signature,
            varName,
            varName);
    	output += FormatCode.indentln(code, indentDepth);
    	return output;    			
    }

    /**
     * Generate the code to Set the contents of a MsgArg from a given signature
     * This code will only work for single dimensional ARRAYS. with signatures
     * of type ai, ab, ad, etc. 
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn ARRAY 
     *      container type
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     * method
     * @return 
     */
    public static String generateSetMsgArgSimpleArrayType(String msgArgName,
                                                          String signature,
                                                          String varName,
                                                          int indentDepth) {
        String output = "";
        output += generateCharPtrArray(varName, signature, indentDepth);
        String arrayArgs = generateArrayArgsForSetMsgArg(varName, signature);
        output += String.format("%s%s.Set(\"%s\"%s);\n",
                                FormatCode.indent(indentDepth),
                                msgArgName,
                                signature,
                                arrayArgs);
        return output;
    }
    
    /**
     * Given a string that contains a String this will produce a char pointer 
     * array to hold the c_str version of the strings.
     * @param varName
     * @param signature
     * @param indentDepth
     */
    public static String generateCharPtrArray(String varName,
                                              String signature,
                                              int indentDepth){
    	String output = "";
    	String code;
    	char c = signature.charAt(1);
        switch(c){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            code = String.format("const char *temp%s[%sNumElements];",
                                 replaceChars(varName), 
                                 varName);
            output += FormatCode.indentln(code, indentDepth);
            code = String.format("for(unsigned int i = 0; i < %sNumElements; i++){",
                                 varName);
            output += FormatCode.indentln(code, indentDepth);
            code = String.format("temp%s[i] = %s[i].c_str();",
                                 replaceChars(varName),
                                 varName);
            output += FormatCode.indentln(code, indentDepth+1);
            output += FormatCode.indentln("}", indentDepth);
            break;
        }
        default:
            break;
        }
        return output;
    }
    
    public static String generateArrayArgsForSetMsgArg(String varName,
                                                       String signature){
    	String output = "";
    	char c = signature.charAt(1);
        switch(c){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            output += String.format(", %sNumElements, temp%s",
                                    varName,
                                    replaceChars(varName));
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default: {
            output += String.format(", %sNumElements, %s",
                                    varName,
                                    varName);
            break;
        }
        }
    	return output;
    } // generateSetMsgArgSimpleArrayType()

    /**
     * Generate the code to Get the contents from a MsgArg.
     * This code will handle container types as well as basic types.
     * {@code generateGetMsgArg("var", "i", "myInt"}
     * will produce the following 
     * {@code var.Get("i", &myInt);}
     * @param msgArgName
     * @param signature
     * @param varName
     * @param indentDepth
     * @return
     */
    public static String generateGetMsgArg(String msgArgName,
                                           String signature,
                                           String varName,
                                           int indentDepth) {
    	String output = "";
    	if(isSignatureContainerType(signature)){
            output += generateGetMsgArgContainerType(msgArgName,
                                                     signature,
                                                     varName,
                                                     false,
                                                     indentDepth);
    	} else {
    	    output += generateGetMsgArgBasicType(msgArgName,
                                                 signature.charAt(0),
                                                 varName,
                                                 false,
                                                 indentDepth);	
    	} 
    	return output;
    } // generateGetMsgArg()
    
    /**
     * Generate the code to Get the contents from a MsgArg.
     * This code will handle container types as well as basic types.
     * {@code generateGetMsgArg("var", "i", "myInt", true, 0)}
     * will produce the following 
     * {@code
     * int myInt; 
     * var.Get("i", &myInt);}
     * @param msgArgName
     * @param signature
     * @param varName
     * @param generateOutVar - if set to true this code will produce the
     *                         variable that that are set by the MsgArg.Get
     *                         call.
     * @param indentDepth
     * @return
     */
    public static String generateGetMsgArg(String msgArgName,
                                           String signature,
                                           String varName,
                                           boolean generateOutVar ,
                                           int indentDepth){
    	String output = "";
    	if(isSignatureContainerType(signature)){
            output += generateGetMsgArgContainerType(msgArgName,
                                                     signature,
                                                     varName,
                                                     generateOutVar,
                                                     indentDepth);
    	} else {
    	    output += generateGetMsgArgBasicType(msgArgName,
                                                 signature.charAt(0),
                                                 varName,
                                                 generateOutVar,
                                                 indentDepth);	
    	}
    	return output;
    }
    
    /**
     * Generate the code to Get the contents of a MsgArg from a given signature.
     * This code will only work for basic data types.
     * 
     * This code assumes the data passed to it is a legal basic data type.
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the character that represents the signature of the
     * basic type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     * method
     * @return 
     */
    public static String generateGetMsgArgBasicType(String msgArgName,
                                                    char signature,
                                                    String varName,
                                                    boolean generateOutVar,
                                                    int indentDepth){
        String output = "";
        String code;
        switch(signature){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            String tempVarName = replaceChars(varName);
            code = String.format("char *temp%s;", 
                                 tempVarName);
            output += FormatCode.indentln(code, indentDepth);
            code = String.format("%s.Get(\"%c\", &temp%s);", 
                                 msgArgName,
                                 signature,
                                 tempVarName);
            output += FormatCode.indentln(code, indentDepth);
            code = String.format("%s = temp%s;",  
                                 varName,
                                 tempVarName);
            output += FormatCode.indentln(code, indentDepth);
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default: { //All of the types above are handled by the default case
            output += String.format("%s%s.Get(\"%c\", &%s);\n", 
                                    FormatCode.indent(indentDepth),
                                    msgArgName,
                                    signature,
                                    varName);
            break;
        }
        }   
        return output;
    } // generateGetMsgArgBasicType()
    
    /**
     * Generate the code to Get the contents of a MsgArg from a given signature.
     * This code will only work for basic data types.
     * 
     * This code assumes the data passed to it is a legal container type.
     * This code assumes all strings are a qcc:String.
     * 
     * There are four container types: STRUCT, ARRAY, VARIANT, and DICT_ENTRY
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - string that represents the signature of the container
     * type
     * @param varName - the name of the variable that holds the value.
     * @param indentDepth - the indent level of the code generated by this
     * method
     * @return 
     */
    public static String generateGetMsgArgContainerType(String msgArgName,
                                                        String signature,
                                                        String varName,
                                                        boolean generateOutVar,
                                                        int indentDepth){
        String output = "";
        switch(signature.charAt(0)){
        case '(': { //STRUCT
            output += generateGetMsgArgStructType(msgArgName,
                                                  signature,
                                                  varName,
                                                  generateOutVar,
                                                  indentDepth);
            break;
        }
        case 'a': {
            if (signature.charAt(1) == '{'){
                output += generateGetMsgArgDictEntryType(msgArgName,
                                                         signature,
                                                         varName,
                                                         generateOutVar,
                                                         indentDepth);
            } else if(signature.charAt(1) == 'a'){
                //Multi-dimensional arrays will be handled as MsgArgs
            	output += String.format("%s%s = %s;\n",
                                        FormatCode.indent(indentDepth),
                                        varName, msgArgName);
            } else if(signature.charAt(1) == '('){
            	output += generateGetMsgArgStructArray(msgArgName,
                                                       signature,
                                                       varName,
                                                       generateOutVar,
                                                       indentDepth);
            } else {
                output += generateGetMsgArgBasicArrayType(msgArgName,
                                                          signature,
                                                          varName,
                                                          generateOutVar,
                                                          indentDepth);
            }
            break;
        }
        case 'v':{
            break;
        }
        default:
            //do nothing
            break;
        }
        return output;
    } // generateGetMsgArgContainerType()
    
    public static String generateGetMsgArgStructType(String msgArgName, 
                                                     String signature, 
                                                     String varName,
                                                     boolean generateOutVar,
                                                     int indentDepth){
    	return generateGetMsgArgStructType(msgArgName, 
                                           signature,
                                           varName, 
                                           false,
                                           null,
                                           generateOutVar,
                                           indentDepth);
    	
    }
  
    /**
     * Generate the code to Get the contents of a MsgArg from a given signature
     * This code will only work for AllJoyn STRUCT Container types. 
     *  
     * This code assumes the signature passed to it is a legal AllJoyn STRUCT 
     * container type. 
     * This code also assumes all strings are qcc::Strings.
     * 
     * @param msgArgName - the variable name of the MsgArg this will Get
     * variables from
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that will be assigned the value
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateGetMsgArgStructType(String msgArgName, 
                                                     String signature, 
                                                     String varName,
                                                     boolean isStructArray,
                                                     String indexVar,
                                                     boolean generateOutVar,
                                                     int indentDepth){
        String output = "";
        String code;
        if(generateOutVar){
            code = String.format(
                "%1$sStruct %1$s;", 
                varName);
            output += FormatCode.indentln(code, indentDepth);

        }
        output += generateTempPointerCodeForGetMsgArg(varName, 
                                                      signature,
                                                      varName + "Struct",
                                                      false,
                                                      isStructArray,
                                                      indexVar,
                                                      indentDepth);
        String listOfArgs = generateStructArgsForGetMsgArg(varName, 
                                                           signature,
                                                           isStructArray,
                                                           indexVar);
        if(isStructArray){
            code = String.format("%s[%s].Get(\"%s\"%s);", 
                                 msgArgName,
                                 indexVar,
                                 signature,
                                 listOfArgs);
        }else{
            code = String.format("%s.Get(\"%s\"%s);", 
                                 msgArgName,
                                 signature,
                                 listOfArgs);
        }
        output += FormatCode.indentln(code, indentDepth);
        output += generateTempPointerCodeForGetMsgArg(varName, 
                                                      signature, 
                                                      varName + "Struct",
                                                      true, 
                                                      isStructArray,
                                                      indexVar,
                                                      indentDepth);
        return output;
    } // generateGetMsgArgStructType()
    
    /**
     * Given a varible name and a valid struct signature this will produce a 
     * comma separated list of all of the elements in the struct with the 
     * modifiers needed to make them work with the MsgArg Get command. 
     * example:
     * varName = myStruct, structSignature =((i)(id(si))) will return
     * ", &myStruct.s0.member0, &myStruct.s1.member0, &myStruct.s1.member1, 
     *  &tempmyStruct_s1_s0_member0Str, &myStruct.s1.s0.member1" 
     * 
     * Note 
     * -the first element starts with a comma qcc::Strings are changed to
     *  temporary c strings.  
     * @param varName
     * @param structSignature
     * @return
     */
    public static String generateStructArgsForGetMsgArg(String varName, 
                                                        String structSignature){
    	return generateStructArgsForGetMsgArg(varName,
                                              structSignature,
                                              false,
                                              null);
    }
    
    
    public static String generateStructArgsForGetMsgArg(String varName, 
                                                        String structSignature, 
                                                        boolean isStructArray,
                                                        String indexVar){
        String output = "";
        int structCount = 0;
        int dictCount = 0;
        int memberCount = 0; 
        for(int i = 1; i < structSignature.length()-1; i++){
            char c = structSignature.charAt(i);
            switch(c){
            case '(' : {
                String innerStructSig = 
                    innerStructSignature(structSignature.substring(
                                             i,
                                             structSignature.length()-1));
                output += generateStructArgsForGetMsgArg(varName
                                                         + ".s"
                                                         + structCount, 
                                                         innerStructSig,
                                                         isStructArray,
                                                         indexVar);
                i += innerStructSig.length()-1;
                structCount++;
                break;
            }
            case ')':
                //Do nothing
                break;
            case 'a' :{
            	if(structSignature.charAt(i+1) == '{'){
                    String tempVarName = replaceChars(varName);
                    output += String.format(", &%s.d%dNumElements, &temp%s_d%d",
                                            varName,
                                            dictCount,
                                            tempVarName,
                                            dictCount);
                    int dictLength = innerDictSignature(
                        structSignature.substring(i)).length();
                    i +=  dictLength - 1;
                    dictCount++;
            	}else if(structSignature.charAt(i+1) == '('){
                    String tempVarName = replaceChars(varName);
                    output += String.format(", &%s.s%dNumElements, &temp%s_s%d",
                                            varName,
                                            structCount,
                                            tempVarName,
                                            structCount);
                    int structLength = innerStructSignature(
                        structSignature.substring(i+1)).length();
                    i += structLength -1;
                    structCount++;
            	} else {
                    switch(structSignature.charAt(i+1)){
                    case 'g': //fall through SIGNATURE type
                    case 'o': //fall through OBJECT PATH type
                    case 's': {
                        output += String.format(", &%s.member%dNumElements, "
                                                + "&%s.member%d",
                    				varName,
                    				memberCount,
                    				varName,
                    				memberCount);
                        break;
                    }
                    default:{
                        output += String.format(", &%s.member%dNumElements,"
                                                + " &%s.member%d",
                    				varName,
                    				memberCount,
                    				varName,
                    				memberCount);
                        break;
                    }
                        	
                    }
            		
                    memberCount++;
                    i++;
            	}
                break;
            }  
                //it is one of the basic types if this case is reached.
            default:{
                switch(c){
                case 'g': //fall through SIGNATURE type
                case 'o': //fall through OBJECT PATH type
                case 's': { //STRING type
                    output += String.format(", &temp%s_member%d",
                                            replaceChars(varName),
                                            memberCount);
                    break;
                }
                case 'd': //fall through DOUBLE type
                case 't': //fall through UINT_64 type
                case 'x': //fall through INT_64 type
                case 'b': //fall through BOOLEAN type
                case 'i': //fall through INT_32 type
                case 'n': //fall through INT_16 type
                case 'q': //fall through UINT_16 type
                case 'u': //fall through UINT_32 type
                case 'y':  //fall through BITE type
                default: {
                    if(isStructArray){
                        output += String.format(", &%s[%s].member%d", 
                                                varName,
                                                indexVar,
                                                memberCount);
                    }else{
                        output += String.format(", &%s.member%d", 
                                                varName,
                                                memberCount);
                    }
                    break;
                }
                }
                memberCount++;
            }
            }

        }
        return output;
    } // generateStructArgsForGetMsgArg()
    
    /**
     * this code is responsible for generating temp pointer code for structs 
     * with strings in them.  
     * example:
     * given a signature (is) will produce
     * char* myVarName_member1; (if Assignment is false)
     * myVarName.member1 =  myVarName_member1; (if assignment is true)
     * the var name used in this method must be the same as the var name used 
     * when calling the "generateStructArgsForGetMsgArg" method or the temporary
     * varabile names will not work.
     * 
     * @param varName - same varName used when calling 
     * 		"generateStructArgsForGetMsgArg" 
     * @param structSignature - same struct signatuer used when calling 
     * 		"generateStructArgsForGetMsgArg" 
     * @param dataType
     * @param isAssignment
     * @param isStructArray
     * @param indexVar
     * @param indentDepth
     * @return
     */
    public static String generateTempPointerCodeForGetMsgArg(
        String varName, 
        String structSignature, 
        String dataType, 
        boolean isAssignment,
        boolean isStructArray,
        String indexVar,
        int indentDepth){
        String output = "";
        String code;
        int structCount = 0;
        int dictCount = 0;
        int memberCount = 0; 
        for(int i = 1; i < structSignature.length()-1; i++){
            char c = structSignature.charAt(i);
            switch(c){
            case '(' : {
                String innerStructSig = 
                    innerStructSignature(
                        structSignature.substring(i,
                                                  structSignature.length()-1));
                output += generateTempPointerCodeForGetMsgArg(
                    varName + ".s" + structCount, 
                    innerStructSig,
                    dataType + "::s" + structCount + "Struct",
                    isAssignment,
                    isStructArray,
                    indexVar,
                    indentDepth);
                i += innerStructSig.length()-1;
                structCount++;
                break;
            }
            case ')':
                //Do nothing
                break;
            case 'a' :{
            	if(structSignature.charAt(i+1) == '{'){
                    String innerDictSig = innerDictSignature(
                        structSignature.substring(i));
                    String innerVarName = varName + ".d" + dictCount;
                    String innerMsgArgName = "temp" + replaceChars(innerVarName);
                    if(isAssignment){
                        output += generateGetMsgArgIndividualDictEntryType(
                            innerMsgArgName, 
                            innerDictSig, 
                            innerVarName,
                            dataType + "::d" + dictCount + "DictEntry",
                            false,
                            0,
                            indentDepth);
                    }else{
                        code = String.format("MsgArg *%s;", innerMsgArgName);
                        output += FormatCode.indentln(code, indentDepth);
                    }
                    i += innerDictSig.length()-1;
                    dictCount++;
            	} else if(structSignature.charAt(i+1) == '('){
                    String innerStructSig = innerStructSignature(
                        structSignature.substring(i+1));
                    String innerVarName = varName + ".s" + structCount;
                    String innerMsgArgName = "temp" + replaceChars(innerVarName);
                    if(isAssignment){
                        output += allocateArrayPtr(innerVarName, 
                                                   dataType
                                                   + "::s"
                                                   + structCount
                                                   + "Struct", 
                                                   innerVarName
                                                   + "NumElements", 
                                                   indentDepth);
                        char counter = GenerateRunnableCode.getCounterVar();
                        code = String.format("for(unsigned int " + counter + " = 0; " + counter + " < %s; " + counter + "++){", 
                                             innerVarName + "NumElements");
                        output += FormatCode.indentln(code, indentDepth);
                        output += generateGetMsgArgStructType(
                            innerMsgArgName,
                            innerStructSig,
                            innerVarName,
                            true,
                            String.valueOf(counter),
                            false,
                            indentDepth+1);
                        output += FormatCode.indentln("}", indentDepth);
                        GenerateRunnableCode.endForLoop();
                    }else{
                        code = String.format("MsgArg *%s;", innerMsgArgName);
                        output += FormatCode.indentln(code, indentDepth); 
                    }
                    i += innerStructSig.length()-1;
                    structCount++;
            	}else{
                    switch(structSignature.charAt(i+1)){
                    case 'g': //fall through SIGNATURE type
                    case 'o': //fall through OBJECT PATH type
                    case 's': { //STRING type
                        if(isAssignment){
                            code = String.format("%s.member%d = new String"
                                                 + "[%s.member%dNumElements];",
                                                 varName,
                                                 memberCount,
                                                 varName,
                                                 memberCount);
                            output += FormatCode.indentln(code, indentDepth);
                            char counter = GenerateRunnableCode.getCounterVar();
                            code = String.format("for(unsigned int " + counter + " = 0; " + counter + " < %s.member"
                                                 + "%dNumElements; " + counter + "++){",
                                                 varName,
                                                 memberCount);
                            output += FormatCode.indentln(code, indentDepth);
                            output += generateGetMsgArgBasicType(
                                "temp"
                                + replaceChars(varName)
                                +"_member"
                                + memberCount
                                + "[" + counter + "]", 
                                structSignature.charAt(i+1), 
                                varName + ".member" + memberCount + "[" + counter + "]", 
                                false, 
                                indentDepth+1);
                            output += FormatCode.indentln("}", indentDepth);
                            GenerateRunnableCode.endForLoop();
                        } else {
                            code = String.format("MsgArg *temp%s_member%d;",
                                                 replaceChars(varName),
                                                 memberCount);
                            output += FormatCode.indentln(code, indentDepth);
                        }
                        break;
                    }
                    default:{
                        // Do Nothing
                        break;
                    }	
                    }
                    memberCount++;
                    i++;
            	}
                break;
            }  
                //it is one of the basic types if this case is reached.
            default:{
                switch(c){
                case 'g': //fall through SIGNATURE type
                case 'o': //fall through OBJECT PATH type
                case 's': { //STRING type
                    
                    String tempVarName = replaceChars(varName);
                    if (isAssignment){
                    	if(isStructArray){
                            code = String.format("%s[%s].member%d = "
                                                 + "temp%s_member%d;",
                                                 varName,
                                                 indexVar,
                                                 memberCount,
                                                 tempVarName,
                                                 memberCount);
                            output += FormatCode.indentln(code, indentDepth);
                    	}else{
                            code = String.format("%s.member%d = "
                                                 + "temp%s_member%d;",
                                                 varName,
                                                 memberCount,
                                                 tempVarName,
                                                 memberCount);
                            output += FormatCode.indentln(code, indentDepth);	
                    	}
                    } else {
                        output += String.format("%schar *temp%s_member%d;\n", 
                                                FormatCode.indent(indentDepth),
                                                tempVarName,
                                                memberCount);
                    }
                    break;
                }
                default: {
                    //Do nothing
                    break;
                }
                }
                memberCount++;
            }
            }
        }
        return output;
    } // generateTempCharPointerCodeForGetMsgArg()
    
    /**
     * This will produce a MsgArg Get command for an array of dictionary entries
     * i.e.  a{si} or a{sa{is}} or a{i(is)} 
     * this will process the full signature or the dictionary entry and assumes
     * it is the top more element nested arrays are handled by there individual 
     * containers.
     * @param msgArgName
     * @param signature
     * @param varName
     * @param generateOutVar
     * @param indentDepth
     * @return
     */
    public static String generateGetMsgArgDictEntryType(String msgArgName, 
                                                        String signature, 
                                                        String varName, 
                                                        boolean generateOutVar,
                                                        int indentDepth){
        String output = "";
        String code;
        code = String.format("const MsgArg *%sDictEntries;", varName);
        output += FormatCode.indentln(code, indentDepth);
        if(generateOutVar){
            code = String.format("size_t %sNumElements;",
                                 varName);
            output += FormatCode.indentln(code, indentDepth);
        }
        code = String.format("%s.Get(\"%s\", &%sNumElements, &%sDictEntries);",
                             msgArgName,
                             signature,
                             varName,
                             varName);
        output += FormatCode.indentln(code, indentDepth);
        if(generateOutVar){
            code = String.format(
                "%1$sDictEntry *%1$s;",
                varName);
            output += FormatCode.indentln(code, indentDepth);
        }
        output += generateGetMsgArgIndividualDictEntryType(varName
                                                           + "DictEntries", 
                                                           signature, 
                                                           varName,
                                                           varName
                                                           + "DictEntry",
                                                           generateOutVar,
                                                           0,
                                                           indentDepth);    
        return output;
    }

    /**
     * This will take a single dictionary element and produce a MsgArg Get 
     * command.  
     * example input:
     * {si}, {sa{is}}, or {i(is)}
     * Note these signatures are not preceded by the 'a' character so are 
     * incomplete ditionary entries.  However MsgArg processes each dictonary 
     * entry individualy.
     * NOTE: this code assumes there is always an array of individual
     * dictionary entries.  This is a valid assumption since a dictionary entry
     * that is not in an array element is not allowed by the standard.
     * @param msgArgName
     * @param signature
     * @param varName
     * @param dataTypeName
     * @param generateOutVar
     * @param loopDepth
     * @param indentDepth
     * @return
     */
    public static String generateGetMsgArgIndividualDictEntryType(
        String msgArgName, 
        String signature, 
        String varName,
        String dataTypeName,
        boolean generateOutVar,
        int loopDepth,
        int indentDepth){
    	String output = "";
    	String valueGetMsgArg = "";
    	String assignTempStructStrings = "";
    	String getStructArray = "";
    	String code;
    	output += allocateArrayPtr(varName, 
                                   dataTypeName, 
                                   varName + "NumElements", 
                                   indentDepth);
        code = String.format("for(unsigned int i%d = 0; i%d < %sNumElements; i%d++){",
                             loopDepth,
                             loopDepth,
                             varName,
                             loopDepth);
        output += FormatCode.indentln(code, indentDepth);
        
        String tempKey = "";
        switch(signature.charAt(2)){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            output += FormatCode.indent("char *tempKey;\n", indentDepth+1);
            tempKey = String.format(", &tempKey", varName);;
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default:{
            tempKey = String.format(", &%s[i%d].key", varName, loopDepth);
            break;
        }    
        }
        
        String tempValue="";
        switch(signature.charAt(3)){
        case 'g':
        case 'o':
        case 's':{
            output += FormatCode.indent("char *tempValue;\n", indentDepth+1);
            tempValue = String.format(", &tempValue", varName);
            break;
        }
        case '(':{
            String innerStruct = innerStructSignature(signature.substring(3));
            output += generateTempPointerCodeForGetMsgArg(
                varName + "[i" + loopDepth +"].value", 
                innerStruct,
                varName + "DictEntry::valueStruct",
                false,
                false,
                "",
                indentDepth+1);
            tempValue = generateStructArgsForGetMsgArg(
                varName +"[i" + loopDepth +"].value", 
                innerStruct,
                false,
                "");
            assignTempStructStrings += generateTempPointerCodeForGetMsgArg(
                varName +"[i" + loopDepth +"].value", 
                innerStruct, 
                varName + "Struct",
                true, 
                false,
                "", 
                indentDepth+1);
            break;
        }
        case 'a':{
            if(signature.charAt(4) == '('){
                output += FormatCode.indentln("MsgArg *tempValue;",
                                              indentDepth+1);
                tempValue = String.format(
                    ", &%s[i%d].valueNumElements, &tempValue", 
                    varName,
                    loopDepth);
                String innerStruct = innerStructSignature(
                    signature.substring(4, signature.length()-1));
                code = String.format("%s[i%d].value = new %s::valueStruct"
                                     + "[%s[i%d].valueNumElements];",
                                     varName, 
                                     loopDepth,
                                     dataTypeName,
                                     varName,
                                     loopDepth);
                getStructArray += FormatCode.indentln(code, indentDepth+1);
                code = String.format("for(unsigned int i%d = 0; i%d < %s[i%d]."
                                     + "valueNumElements; i%d++){",
                                     loopDepth+1,
                                     loopDepth+1,
                                     varName,
                                     loopDepth,
                                     loopDepth+1);
                getStructArray += FormatCode.indentln(code, indentDepth+1);
                getStructArray += generateGetMsgArgStructType(
                    "tempValue", 
                    innerStruct, 
                    varName+ "[i" + loopDepth +"].value[i" + (loopDepth+1) + "]",
                    true,
                    "i" + (loopDepth + 1),
                    false,
                    indentDepth+2);
                getStructArray += FormatCode.indentln("}", indentDepth+1);
            }else if(signature.charAt(4) == '{'){
                String innerDict = innerDictSignature(signature.substring(3));
                output += FormatCode.indentln("const MsgArg *tempValueArg;",
                                              indentDepth+1);
                tempValue = String.format(", &%s[i%d].valueNumElements, "
                                          + "&tempValueArg", 
                                          varName, 
                                          loopDepth);
                valueGetMsgArg = generateGetMsgArgIndividualDictEntryType(
                    "tempValueArg", 
                    innerDict,
                    varName+ "[i" + loopDepth + "].value",
                    dataTypeName + "::valueDictEntry",
                    generateOutVar,
                    loopDepth+1,
                    indentDepth+1);
            }else if(signature.charAt(4) == 'a'){
                UIOutput.LogError(String.format(
                                       "The signature %s is not "
                                       + "yet supported by the codegen tool", 
                                       signature));
            }else{
                switch(signature.charAt(4)){
                case 'g':
                case 'o':
                case 's':{
                    output += FormatCode.indentln("char **tempValue;",
                                                  indentDepth +1);
                    tempValue = String.format(", &%s[i%d].valueNumElements, "
                                              + "&tempValue",
                                              varName,
                                              loopDepth);
                    break;
                }
                default:{
                    tempValue = String.format(", &%s[i%d].valueNumElements, "
                                              + "&%s[i%d].value",
                                              varName,
                                              loopDepth,
                                              varName,
                                              loopDepth);
                    break;
                }
                }
            }
            break;
        }
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'b': //fall through BOOLEAN type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default:{
            tempValue = String.format(", &%s[i%d].value", varName, loopDepth);
            break;
        }
        }
        
        code = String.format("%s[i%d].Get(\"%s\"%s%s);",
                             msgArgName,
                             loopDepth,
                             signature.substring(1),
                             tempKey,
                             tempValue);
        output += FormatCode.indentln(code, indentDepth+1);
        switch(signature.charAt(2)){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            code = String.format("%s[i%d].key = tempKey;",
                                 varName,
                                 loopDepth);
            output += FormatCode.indentln(code, indentDepth+1);
            break;
        }
        default:
            break;
        }
        switch(signature.charAt(3)){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's':{ //STRING type
            code = String.format("%s[i%d].value = tempValue;", 
                                 varName,
                                 loopDepth);
            output += FormatCode.indentln(code, indentDepth+1);
            break;
        }
        default:
            break;
        }
        output += getStructArray;
        //this will assign the temporary char* to a String from a struct
        output += assignTempStructStrings;
        //this will process the contents of an inner dictonary type
        output += valueGetMsgArg; 
        output += FormatCode.indentln("}", indentDepth);
    	return output;    
    } // generateGetMsgArgDictEntryType()

    
    /**
     * Generate the code to Get the contents of a MsgArg from a given signature
     * This code will only work for single dimensional ARRAYS of a STRUCT. 
     * Example: a(ii), a(is(ii)), a(di), etc. 
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn ARRAY 
     *      container type, that contains an array of a struct
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateGetMsgArgStructArray(String msgArgName, 
                                                      String signature, 
                                                      String varName, 
                                                      boolean generateOutVar, 
                                                      int indentDepth){
    	String output = "";
    	String code;
    	code = String.format(
            "MsgArg *temp%sArg;",
            varName);
    	output += FormatCode.indentln(code, indentDepth);
    	if(generateOutVar){
            code = String.format(
                "size_t %sNumElements;",
                varName);
            output += FormatCode.indentln(code, indentDepth); 
    	}

        output += generateTempPointerCodeForGetMsgArg(varName + "[i]",
                                                      signature.substring(1, signature.length()-1),
                                                      varName + "Struct",
                                                      false,
                                                      true,
                                                      null,
                                                      indentDepth);


    	code = String.format(
            "%s.Get(\"%s\", &%sNumElements, &temp%sArg);",
            msgArgName,
            signature,
            varName,
            varName);
    	output += FormatCode.indentln(code, indentDepth);
    	if(generateOutVar){
            code = String.format(
                "%1$sStruct *%1$s = new %1$sStruct[%1$sNumElements];", 
                varName);
    	} else {
            code = String.format(
                "%1$s = new %1$sStruct[%1$sNumElements];",
                varName);
    	}
    	output += FormatCode.indentln(code, indentDepth);
        char counter = GenerateRunnableCode.getCounterVar();
    	code = String.format(
            "for(unsigned int " + counter + "=0; " + counter + " < %sNumElements; " + counter + "++){", 
            varName);
    	output += FormatCode.indentln(code, indentDepth);
    	code = String.format(
            "temp%sArg[" + counter + "].Get(\"%s\"%s);",
            varName,
            signature.substring(1),
            generateStructArgsForGetMsgArg(varName+"[" + counter + "]",
                                           signature.substring(1)));
    	output += FormatCode.indentln(code, indentDepth+1);
        output += generateTempPointerCodeForGetMsgArg(varName + "[" + counter + "]",
                                                      signature.substring(1, signature.length()-1),
                                                      varName + "Struct",
                                                      true,
                                                      false,
                                                      null,
                                                      indentDepth + 1);
     
    	output += FormatCode.indentln("}", indentDepth);
        GenerateRunnableCode.endForLoop();
    	return output;
    }

    /**
     * Generate the code to Get the contents of a MsgArg from a given signature
     * This code will only work for single dimensional ARRAYS. with signatures
     * of type ai, ab, ad, etc. 
     *  
     * This code assumes 
     *     -the signature passed to it is a legal AllJoyn ARRAY 
     *      container type
     *     -a value exists that holds how many items are in the array and
     *      it uses the same name as the varName with the suffix NumElements 
     * 
     * @param msgArgName - the variable name of the MsgArg this will be Set
     * @param signature - the string representing the signature of a STRUCT 
     *                    container type
     * @param varName - the name of the variable that holds the value.
     *                  Note the actual value could be used as the varName
     * @param indentDepth - the indent level of the code generated by this
     *                      method
     * @return 
     */
    public static String generateGetMsgArgBasicArrayType(
        String msgArgName, 
        String signature, 
        String varName, 
        boolean generateOutVar, 
        int indentDepth){
        String output = "";
        String code;
        char c = signature.charAt(1);
        switch(c){
        case 'g': //fall through SIGNATURE type
        case 'o': //fall through OBJECT PATH type
        case 's': { //STRING type
            output += FormatCode.indentln("const MsgArg *stringArray;",
                                          indentDepth);
            output += FormatCode.indentln("char* tempString;", indentDepth);
            if(generateOutVar){
            	code = String.format("size_t %sNumElements;", varName);
            	output += FormatCode.indentln(code, indentDepth);
            }
            output += String.format("%s%s.Get(\"%s\", &%sNumElements, "
                                    + "&stringArray);\n",
                                    FormatCode.indent(indentDepth),
                                    msgArgName,
                                    signature,
                                    varName);
            if(generateOutVar){
            	code = String.format("String * %1$s = new String[%1$sNumElements];", varName);
            	output += FormatCode.indentln(code, indentDepth);
            }
            output += String.format(
                "%sfor(unsigned int i = 0; i < %sNumElements; i++){\n",
                FormatCode.indent(indentDepth),
                varName);
            output += String.format(
                "%sstringArray[i].Get(\"%c\", &tempString);\n",
                FormatCode.indent(indentDepth+1),
                c);
            output += String.format("%s%s[i] = tempString;\n",
                                    FormatCode.indent(indentDepth+1),
                                    varName);
            output += FormatCode.indentln("}", indentDepth);
            break;
        }
        case 'b': //fall through BOOLEAN type
        case 'd': //fall through DOUBLE type
        case 't': //fall through UINT_64 type
        case 'x': //fall through INT_64 type
        case 'i': //fall through INT_32 type
        case 'n': //fall through INT_16 type
        case 'q': //fall through UINT_16 type
        case 'u': //fall through UINT_32 type
        case 'y': //fall through BITE type
        default: {
            code = String.format("%s * temp%sArray;", mapType(c), varName);
            output += FormatCode.indentln(code, indentDepth);
            output += String.format("%s%s.Get(\"%s\", &%sNumElements, &temp%sArray);\n",
                                    FormatCode.indent(indentDepth),
                                    msgArgName,
                                    signature,
                                    varName,
                                    varName);
            if(generateOutVar){
                code = String.format("%s * %s = new %s[%sNumElements];", 
                                     mapType(c),
                                     varName,
                                     mapType(c),
                                     varName);
                output += FormatCode.indentln(code, indentDepth);
            }
            output += String.format(
                "%sfor(unsigned int i = 0; i < %sNumElements; i++){\n",
                FormatCode.indent(indentDepth),
                varName);
            output += String.format("%s%s[i] = temp%sArray[i];\n",
                                    FormatCode.indent(indentDepth+1),
                                    varName,
                                    varName);
            output += FormatCode.indentln("}", indentDepth);
            break;
        }
        }
        return output;
    } // generateGetMsgArgBasicArrayType()

    /**
     * Write the ifndef statements in the file.
     */
    protected void writeIfNDef() {
        String output = "";
        output += "#ifndef _" 
            + this.fileName.replace(".h", "_").toUpperCase() 
            + "H\n"
            
            + "#define _" 
            + this.fileName.replace(".h", "_").toUpperCase() 
            + "H\n\n";    		
        writeCode(output);
    } // writeIfNDef()

} // class CodeWriter
