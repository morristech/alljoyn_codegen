/*******************************************************************************
 * Copyright 2010 - 2011, 2013 Qualcomm Innovation Center, Inc.
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

import java.util.ArrayList;

/**
 * Child class of CodeWriter
 * 
 * Only one instance should be instantiated to create/generate all the service
 * files.  Contains methods that create and write the service files and helper
 * methods specific to the service files.
 */
class WriteServiceCode extends WriteCode{

    private boolean writeSetMethod;
    private boolean writeGetMethod;

    public WriteServiceCode() {
        if(ParseAJXML.useFullNames) {
            objName = inter.className;
        }
        else {
            objName = inter.getName();
        } 
        objName += "Service";
        writeSetMethod = false;
        writeGetMethod = false;
    }

    /**
     * Creates the header file and write the generated service class definition
     * in it.
     * @see #writeHeaderIncludes(boolean, boolean)
     * @see #writeClassDef()
     * @see #writeIfNDef()
     */
    public void writeHeaderFile(){
        fileName = objName 
            + ".h";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN " 
                               + fileName 
                               + "!!! EXITING", 0);
        }
        writeIfNDef();
        writeHeaderComments();
        writeHeaderIncludes(true, false);
        writeStructs();
        writeClassDef();
        
        closeFile();
    }
	
    /**
     * Creates the service cc file which contains most of the generated
     * functions that hides the alljoyn API from the developer.
     * @see #writeCCIncludes()
     * @see #writeConstructor()
     * @see #writeMethodHandlers()
     * @see #writeMethodReply()
     * @see #writeSignals()
     */
    public void writeCCFile(){
        fileName = objName 
            + ".cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN " 
                               + fileName 
                               + "!!! EXITING", 0);
        }
        
        writeCCComments();
        writeCCIncludes();
        writeConstructor();
        writeMethodHandlers();
        writeMethodReply();
        writeSignals();
        writeGetSet();
        
        closeFile();
    }
	
    /**
     * Creates the serviceMethods.cc file and writes the empty method handlers
     * where the developer can fill in with their implementation.
     * @see #writeCCIncludes()
     * @see #writeMethods()
     */
    public void writeDevCCFile(){
        fileName = objName 
            + "Methods.cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN " 
                               + fileName 
                               + "!!! EXITING", 0);
        }
        
        writeDevComments();
        writeCCIncludes();
        writeMethods();
        
        closeFile();
    }
	
    /**
     * Creates and writes the sample Main file which contains a simple service
     * implementation.
     * @see #writeHeaderIncludes(boolean, boolean)
     * @see #writeMainFunction()
     */
    public void writeMainFile(){
        fileName = "ServiceMain.cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN " 
                               + fileName 
                               + "!!! EXITING", 0);
        }
        
        writeMainComments();
        writeHeaderIncludes(true, true);
        writeMainFunction();
        
        closeFile();
    }
	
    /**
     * Writes the service main function to the service main file.
     */
    private void writeMainFunction(){
        String output = "";
		String commentStr;
		String code;
		
        commentStr = "Static top level message bus manager - this is a static " +
        		"global in this file as it needs to be accessible in both " +
        		"main() and the signal handler for SIGINT.";
        output += FormatCode.blockComment(commentStr);	
        output += "static BusAttachmentMgr* AllJoynMgr = NULL;\n\n";
        
        commentStr = "SigintHandler()\n" +
        		"This is the handler for the Int signal (i.e. Ctrl+C). With" +
        		"out the signal handler the program will exit without " +
        		"stopping the bus which may result in a memory leak.";
        output += FormatCode.blockComment(commentStr);	
        output += "static void SigIntHandler(int sig){\n"; 
        output += FormatCode.indentln("AllJoynMgr->Stop();",1);
        output +="} /* SigIntHandler() */\n\n";
		
        commentStr = "main()\n" +
        		"The entry point for the executable.";
        output += FormatCode.blockComment(commentStr);
        output += "int main(int argc, char **argv, char**envArg){\n"; 
        output += FormatCode.indentln("QStatus status = ER_OK;", 1); 
        code = "printf(\"AllJoyn Library version: %s\\n\", ajn::GetVersion());";
        output += FormatCode.indentln(code, 1); 
        code = "printf(\"AllJoyn Library build info: %s\\n\", ajn::GetBuildInfo());\n";
        	output += FormatCode.indentln(code, 1);
        commentStr = "Install SIGINT handler so Ctrl + C deallocates " +
        		"memory properly";
        output += FormatCode.comment(commentStr, 1);
        
        output += FormatCode.indentln("signal(SIGINT, SigIntHandler);\n", 1); 
        
		commentStr = "Create the bus attachment manager that handles the " +
				"interactions with the message bus.\n" +
				"The second argument is a boolean indicating whether remote " +
				"device discovery is enabled.";
		output += FormatCode.blockComment(commentStr, 1);
        
        code = String.format(
        		"AllJoynMgr = new BusAttachmentMgr(\"%sApp\", \"%s\", true);\n",
        		config.className, config.wellKnownName);
        output += FormatCode.indentln(code, 1); 

        for(ObjectData obj : config.objects) {
            commentStr = String.format(
                        "Create a %s object using the BusAttachment owned by the bus " +
                        "manager and preferred path. If there were multiple services " +
                        "to be started from this application then each service object " +
                        "would be created here using the same bus attachment object."
                        , obj.objName);
            output += FormatCode.blockComment(commentStr, 1);
            String className = "";
            if(ParseAJXML.useFullNames) {
                className = obj.inter.className;
            }
            else {
                className = obj.inter.getName();
            }
            className += "Service";
            code = String.format("%s %s(*AllJoynMgr->GetBusAttachment(),", 
                        className,
            		obj.objName);
            output += FormatCode.indentln(code, 1);
            code = "*AllJoynMgr->GetBusListener(),";
            output += FormatCode.indentln(code, 2);   
            code = String.format("\"%s\");", obj.objPath);
            output += FormatCode.indentln(code, 2);
        }

		
        writeCode(output);
        output = "";
        commentStr = "Register the service object with the bus and start the " +
                     "service. If there are multiple services that are going to " +
                     "be hosted out of this application then calls to " +
                     "RegisterBusObject should be made for each service object " +
                     "BEFORE calling StartService(). Once the interrupt signal" +
                     "has been received clean up by calling Delete().";
        output += FormatCode.blockComment(commentStr, 1);
        for(ObjectData obj : config.objects) {
            code = String.format(
                    "status = AllJoynMgr->RegisterBusObject(%s);\n",
		    obj.objName);
            output += FormatCode.indentln(code, 1);
        }
        
        code = String.format("status = AllJoynMgr->StartService(\"%s\");\n", config.wellKnownName);
        output += FormatCode.indentln(code, 1); 

        output += FormatCode.indentln("AllJoynMgr->WaitForSigInt();\n", 1);
        output += FormatCode.indentln("AllJoynMgr->Delete();", 1);
        output += FormatCode.indentln("delete AllJoynMgr;", 1);
        output += FormatCode.indentln("AllJoynMgr = NULL;\n", 1);
        output += FormatCode.indentln("return (int) status;", 1);
        output += "} /* main() */\n";
		
        writeCode(output);
    }
	
    /**
     * Writes the service class definition. Should be called by the service
     * header file
     */
    private void writeClassDef() {
    	ArrayList<MethodDef> tempMethodList;
    	ArrayList<SignalDef> tempSignalList;
    	ArrayList<PropertyDef> tempPropList;
    	MethodDef tempMeth;
    	SignalDef tempSig;
        String output = ""; 
        String commentStr;
        String code;
        output += "\n";
        commentStr = String.format(
        		"CLASS: %s\n" +
        		"This class is a child of the busObject class. This is " +
        		"required so that it can interact directly with the bus.", 
        		objName);
        output += FormatCode.blockComment(commentStr);

        output += String.format("class %s : public BusObject{\n\n", objName);

        output += FormatCode.indentln("public:\n", 1);
		commentStr = String.format(
				"METHOD: %1$s()\n" +
				"Constructor for the %1$s class. Takes in pointer to the " +
				"BusAttacment it will register with and the desired path for " +
				"the object.",
				objName);
		output += FormatCode.blockComment(commentStr, 2);

        code = String.format("%s(BusAttachment &bus, MyBusListener &busListener, const char *path);\n", 
        		objName);
        output += FormatCode.indentln(code, 2); 
		
        writeCode(output);
        output = "";
		
        tempMethodList = inter.getMethods();
        tempSignalList = inter.getSignals();
			
        for(int j = 0; j < tempMethodList.size(); j++){
            tempMeth = tempMethodList.get(j);
            commentStr = String.format(
            		"Method: %1$sHandler()\n" +
               		"This is the method that gets called by AllJoyn when " +
               		"the service receives a method call for the %1$s method.",
               		tempMeth.getName());
            output += FormatCode.blockComment(commentStr, 2);
            code = String.format(
             		"void %sHandler(const InterfaceDescription::Member* member, Message& msg);\n",
               		tempMeth.getName());
            output += FormatCode.indentln(code, 2);
        }
		
        for(int j = 0; j < inter.getProperties().size(); j++){
            if(inter.getProperties().get(j).getAccess().equals("read") ||
                inter.getProperties().get(j).getAccess().equals("readwrite")){
                commentStr = "METHOD: Get()\n" +
				"Overwrites the Get method of the BusObject class " +
				"to support the properties specified in the " +
				"interfaces. Handles a GetProperty call.";
		output += FormatCode.blockComment(commentStr, 2);
                output += FormatCode.indentln("QStatus Get(const char " +
                  		"*ifcName, const char *propName, MsgArg& val);\n", 2); 
                writeGetMethod = true;
                break;
            }
        }
		
        for(int j = 0; j < inter.getProperties().size(); j++){
            if(inter.getProperties().get(j).getAccess().equals("write") ||
               inter.getProperties().get(j).getAccess().equals("readwrite")){
		commentStr = "METHOD: Set()\n" +
				"Overwrites the Set method of the BusObject " +
				"class to support the properties specified in " +
				"the interfaces. Handles a SetProperty call.";
		output += FormatCode.blockComment(commentStr, 2);
                output += FormatCode.indentln("QStatus Set(const char " +
                		"*ifcName, const char *propName, MsgArg& val);\n", 2); 
                writeSetMethod = true;
                break;
            }
        }
		
        writeCode(output);
        output = "";
        commentStr = String.format("METHOD: ~%1$s()\n" +
        		"Destructor for %1$s.", objName);
        output += FormatCode.blockComment(commentStr, 2);
        code = String.format("~%s(){}\n", objName);
        output += FormatCode.indentln(code, 2);
        
        tempMethodList = inter.methods;

        output += FormatCode.indentln("private:", 1);
        	
        for(int j = 0; j < tempMethodList.size(); j++){
            tempMeth = tempMethodList.get(j);
            String methodName = tempMeth.getName();
            commentStr = String.format("METHOD : %1$s()\n" +
            		"This method is called by the %1$sHandler with the " +
               		"corresponding input and output arguments. This " +
               		"method is empty, the developer should fill it with " +
               		"the developer's implementation of the %1$s method handler.\n", 
               		methodName);
            output += FormatCode.blockComment(commentStr, 2);
            code = "";
            if(tempMeth.argList.isEmpty()){
            	code += String.format("void %s();\n", methodName);
               	output += FormatCode.indentln(code, 2);
            }else{
              	code += String.format("void %s(%s);\n", 
               			methodName,
              			generateArgs(tempMeth.argList));
              	output += FormatCode.indentln(code, 2);
            }
 	    commentStr = String.format("METHOD %1$sMethodReply()\n" +
    					"Pass in the method call message and the processed " +
    					"output to send a method reply message to the caller.\n" +
    					"This method should be called in the %1$sHandler for " +
    					"synchronous implementations.", 
    					methodName); 
    	    output += FormatCode.blockComment(commentStr, 2);
            if(tempMeth.getRetType().equals("NULL")){
              	code = String.format("QStatus %sMethodReply(Message& msg);\n",
               			methodName);
               	output += FormatCode.indentln(code, 2);
            }else{
               	code = String.format("QStatus %sMethodReply(Message& msg, %s);\n",
               			methodName,
               			generateMethodReplyArgs(tempMeth.argList, 
               					tempMeth.outArgCount));
              	output += FormatCode.indentln(code, 2);
            }
        }
        writeCode(output);
        output = "";

        tempSignalList = inter.getSignals();
			
        for(int j = 0; j < tempSignalList.size(); j++){
            tempSig = tempSignalList.get(j);
            commentStr = String.format("METHOD: Sent%1$s()\n" +
               		"Sends out the \"%1$s\" signal with the specified " +
               		"arguments. Leave the destination field as NULL for " +
               		"broadcast or set it as the desired recipient's " +
               		"unique or well-known name.", 
               		tempSig.getName());
            output += FormatCode.blockComment(commentStr, 2);
            if(tempSig.argList.isEmpty()){
               	code = String.format("QStatus Send%s(const char* destination=NULL);\n", 
               			tempSig.getName());
               	output += FormatCode.indentln(code, 2);
            }else{
               	code = String.format("QStatus Send%s(%s, const char* destination=NULL);\n", 
               			tempSig.getName(),
               			generateArgs(tempSig.argList));
               	output += FormatCode.indentln(code, 2);
            }
        }
		
        commentStr = "MEMBER: myBusAttachment\n" +
        		"Pointer to the busAttachment this service is registered with.";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("BusAttachment* myBusAttachment;\n", 2); 
        
        commentStr = "MEMBER: myBusListener\n" +
        		"Pointer to the busListener this service is registered with.";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("MyBusListener* myBusListener;\n", 2); 
        
        tempPropList = inter.getProperties();
        output += generateProperties(tempPropList, 2);
		
        output += "\n};\n\n#endif";
		
        writeCode(output);
    }
	
    /**
     * Writes the empty methods where developer fill in their method handler
     * implementation.
     */
    private void writeMethods(){
    	MethodDef tempMethod;
    	String output = "";
    	String code;
        for(int j = 0; j < inter.methods.size(); j++){
            tempMethod = inter.methods.get(j);
            code = String.format(
                "METHOD: %1$s()\n" +
                "This method is called by the %1$sHandler with the " +
                "corresponding input and output arguments. This method " +
                "is empty, the developer should fill it with the " +
                "developer's implementation of the %1$s method handler.", 
                tempMethod.getName());
            output += FormatCode.blockComment(code);
            code = String.format("void %s::%s(%s){\n",
                                 objName,
                                 tempMethod.getName(),
                                 generateArgs(tempMethod.argList));
            output += code;
                
            /*
             * If the runnable flag is true, print out runnable code,
             * otherwise leave blank
             */
            if(config.runnable){
                output += GenerateRunnableCode.generateMethodHandler(tempMethod);
    				
                if(j == inter.methods.size()-1){
                    output += "\n\n\n";
                    output +=
                        GenerateRunnableCode.generateSendSignals(inter.signals);
                 }
            }else{
                output += "/* Fill in method handler implementation"
                    + " here. */\n";
            }

            output += "}/* " 
                + tempMethod.getName() 
                + "() */\n\n";
        }
    	writeCode(output);
    }

    /**
     * Writes the service object constructor code to the file.
     */
    private void writeConstructor(){
        String output = "";
        String varName;
        String commentStr;
        String code;
        commentStr = String.format("METHOD: %1$s()\n" +
                        "Constructor for the %1$s class. Takes in pointer to the " +
                        "BusAttachment it will register with and the desired path for " +
                        "the object",
                        objName);
        output += FormatCode.blockComment(commentStr);
        output += String.format("%1$s::%1$s(BusAttachment &bus, MyBusListener &busListener, const char* path)\n",
                        objName);

        output += FormatCode.indentln(": BusObject(path, false)", 1);
        output += "{\n"
            + indentDepth;
        output += "myBusAttachment = &bus;\n"
            + indentDepth;

        output += "myBusListener = &busListener;\n\n"
            + indentDepth;

        output += "/* This is where the developer should initialize any"
            + " property variables. */\n";        // Generate property initialization for basic types
        if(config.runnable){
            ArrayList<PropertyDef> tempPropList;
            tempPropList = inter.getProperties();
            output += GenerateRunnableCode.generatePropertyInitialization(tempPropList, 1);
            output += "\n";
        }
        output += indentDepth;

        output += topBracket
            + indentDepth
            + "  The following code gets the specified interface, "
            + "or creates it if it \n"
            + indentDepth 
            + "  doesn't exist, and populates it"
            + " with the appropriate\n";
        output += indentDepth
            + "  content from the interface specified in the XML"
            + " descriptions.\n"
            + indentDepth
            + bottomBracket
            + indentDepth;

        varName = inter.getFullName().replaceAll("\\.", "_");
        int numInterfaces = 1;
        if(inter.isDerived) {
            numInterfaces = inter.parents.size();
        }
        InterfaceDescription curInterface = inter;
        output += "const InterfaceDescription* "
            + "getIface = NULL;\n";
        for(int i = 0; i < numInterfaces; i++) {
            if(inter.isDerived) {
                curInterface = inter.parents.get(i);
            }
            output += indentDepth
                + "getIface"
                + " = bus.GetInterface(\""
                + curInterface.getFullName()
                + "\""
                + ");\n"
                 + indentDepth;
            output += "if(!getIface) {\n"
                + indentDepth + indentDepth;
            output += "InterfaceDescription* "
                    + "createIface"
                    + " = NULL;\n"
                    + indentDepth
                    + indentDepth;
            if(curInterface.isSecure){
                output += "/* This interface is created as a secure"
                    + " interface according to the XML file */\n"
                    + indentDepth
                    + indentDepth;
                output += "bus.CreateInterface(\""
                    + curInterface.getFullName()
                    + "\", "
                    + "createIface"
                    + ", true);\n"
                    + indentDepth;
            }else{
                output += "bus.CreateInterface(\""
                    + curInterface.getFullName()
                    + "\", "
                    + "createIface"
                    + ", false);\n"
                    + indentDepth;
            }
            writeCode(output);
            output = "";
            this.writeInterfaceMember("createIface", curInterface);
            output += indentDepth;
            output += "/* Activate the interface which enables it to be"
                    + " used/accessed by objects on the bus. */\n"
                    + indentDepth
                    + indentDepth;
            output += "createIface"
                    + "->Activate();\n"
                    + indentDepth;
            output += getAddInterfaceComment();
            output += indentDepth
                    + indentDepth
                    + "AddInterface(*"
                    + "createIface"
                    + ");\n\n"
                    + indentDepth;
            output += getAddMethodHandlerComment();
            output += generateAddMethods(curInterface, "createIface")
                    + "}\n"
                    + indentDepth;
            output += "else {\n"
                    + indentDepth;
            output += getAddInterfaceComment();
            output += indentDepth
                    + indentDepth
                    + "AddInterface(*"
                    + "getIface"
                    + ");\n\n"
                    + indentDepth;
            output += getAddMethodHandlerComment();
            output += generateAddMethods(curInterface, "getIface")
                + "}\n";
        }
        output += "\n";
        output += "} /* "
            + objName
            + "() */\n\n";
        writeCode(output);
    }

    private String getAddInterfaceComment() {
        String output = "";
        output += indentDepth
                + topBracket
                + indentDepth
                + indentDepth
                + "  Add the interfaceDescription to the "
                + objName
                + " object so that it has a\n";
        output += indentDepth
                + indentDepth
                + "  definition of the interface. This is required for"
                + " adding method handlers to\n";
        output += indentDepth
                + indentDepth
                + "  the object.\n"
                + indentDepth
                + indentDepth
                + bottomBracket;
        return output;
    }

    private String getAddMethodHandlerComment() {
        String output = "";
        output += indentDepth
                + topBracket
                + indentDepth
                + indentDepth
                + "  Add method handler(s) for the method(s) defined in the"
                + " interface. A method\n";
        output += indentDepth
                + indentDepth
                + "  handler calls the developer's implementation of the"
                + " method.\n";
        output += indentDepth
                + indentDepth
                + "  Note: The static_cast converts the handler function"
                + " to the MethodHandler\n";
        output += indentDepth
                + indentDepth
                + "  type so it can be called correctly by the AllJoyn API.\n"
                + indentDepth
                + indentDepth
                + bottomBracket
                + indentDepth;
        return output;
    }
    
    /**
     * Writes the internal method handlers that call the developer's method
     * handler.
     */
    private void writeMethodHandlers(){
        String output = "";
        String outArgNames;
        String commentStr;
        String code;
        MethodDef tempMethod;
        ArgDef arg;

        for(int j = 0; j < inter.getMethods().size(); j++){
                outArgNames = "";
                tempMethod = inter.getMethods().get(j);
                commentStr = String.format(
                    "METHOD: %1$sHandler()\n" +
                    "This is the method that gets called by the AllJoyn API " +
                    "when the service receives a method call for the %1$s method.",
                    tempMethod.getName());
                output += FormatCode.blockComment(commentStr);
                output += String.format(
                    "void %s::%sHandler(const InterfaceDescription::Member* member, " +
                    "Message& msg){\n",
                    objName,
                    tempMethod.getName());
                output += FormatCode.indentln("const ajn::MsgArg* args;", 1);
                output += FormatCode.indentln("size_t numArgs;\n", 1);
                output += FormatCode.comment("Extract the input arguments", 1);
                output += FormatCode.indentln("msg->GetArgs(numArgs, args);\n", 1);
                commentStr =
                    "Make sure the number of arguments is correct before " +
                    "passing them to the method handler.";
                output += FormatCode.comment(commentStr, 1);
                code = String.format("if(numArgs == %s){\n",
                                     tempMethod.inArgCount);
                output += FormatCode.indentln(code, 1);
//get values from input arguments struct
                int count = 0;
                for(int k = 0; k < tempMethod.argList.size(); k++){
                    arg = tempMethod.argList.get(k);
                    if(arg.getArgDirection().equals("in")){
                        if(structArgList.contains(arg)){
                            output += generateGetMsgArg("args[" + k + "]",
                                                        arg.getArgType(),
                                                        arg.getArgName(),
                                                        true,
                                                        2);
                        }else if(isBasicArrayContainerType(arg.getArgType()) &&
                                 (arg.getArgType().charAt(1) == 's' ||
                                  arg.getArgType().charAt(1) == 'o' ||
                                  arg.getArgType().charAt(1) == 'g')){
                            output += generateGetMsgArg("args["+ count +"]", arg.getArgType(), arg.argName, true, 2);
                        }else if( dictEntryArgList.contains(arg) ||
                                  arrayContainerTypeArgList.contains(arg)){
                            output += generateGetMsgArg("args["+ count +"]", arg.getArgType(), arg.argName, true, 2);
                        }
                        count++;
                    }
                }
                for(int k = 0; k < tempMethod.argList.size(); k++){
                    arg = tempMethod.argList.get(k);
                    if(arg.getArgDirection().equals("out")){
                        output += generateTempArg(arg, 2);
                        outArgNames += ", "
                            + arg.getArgName();
                        if(isBasicArrayContainerType(arg.getArgType()) ||
                           dictEntryArgList.contains(arg) ||
                           (arg.getArgType().charAt(0) == 'a' &&
                            arg.getArgType().charAt(1) == '(')){
                            outArgNames += ", "
                                + arg.getArgName()
                                + "NumElements";
                        }
                    }
                }
                output += "\n"
                    + FormatCode.comment("Call the function the dev implemented "
                                         + "for this method handler with the extracted arguments", 2);
                code =String.format("%s(%s);\n",
                                    tempMethod.getName(),
                                    generateMethodWrapperArgs(tempMethod.argList));
                output += FormatCode.indentln(code, 2);

                if(!tempMethod.noReply){
                    commentStr =
                        "The variables should now contain the correct output " +
                        "values, call the methodReply function.\n" +
                        "Note: For asynchronous methods, comment out the call " +
                        "below and call it when the service is ready to send " +
                        "the reply.";
                    output += FormatCode.comment(commentStr, 2);
                    if(tempMethod.outArgCount == 0){
                        code = String.format("%sMethodReply(msg);",
                                             tempMethod.getName());
                        output += FormatCode.indentln(code, 2);
                    }else{
                        code = String.format("%sMethodReply(msg%s);",
                                             tempMethod.getName(),
                                             outArgNames);
                        output += FormatCode.indentln(code, 2);
                    }
                }

                output += FormatCode.indentln("}", 1);
                output += "} /* "
                    + tempMethod.getName()
                    + "Handler() */\n\n";
        }
        writeCode(output);
    }
    
    /**
     *  Writes the method reply functions that are used to return method calls.
     */
    private void writeMethodReply(){
        String output = "";
        String commentStr = "";
        MethodDef tempMethod;

        for(int j = 0; j < inter.getMethods().size(); j++){
                tempMethod = inter.getMethods().get(j);

                if(!tempMethod.noReply){
                    commentStr = String.format(
                        "METHOD: %1$sMethodReply()\n" +
                        "Pass in the method call message and the processed " +
                        "output to send a method reply message to the caller.\n" +
                        "This method should be called in the %1$sHandler for " +
                        "synchronous implementations.",
                        tempMethod.getName());
                    output += FormatCode.blockComment(commentStr);
                    output += "QStatus "
                        + objName
                        + "::"
                        + tempMethod.getName()
                        + "MethodReply(Message& msg";
                    if(tempMethod.outArgCount != 0){
                        output += ", ";
                    }
                    output += generateMethodReplyArgs(tempMethod.argList,
                                                      tempMethod.outArgCount)
                        + "){\n"
                        + indentDepth;
                    output += "QStatus status = ER_OK;\n";

                    commentStr =
                        "Create the MsgArg array, populate it with the return " +
                        "arguments then use it to send the method reply message.";
                    output += FormatCode.blockComment(commentStr, 1);

                    output += String.format(
                        "%sMsgArg args[%d];\n",
                        FormatCode.indent(1),
                        tempMethod.outArgCount);
                    ArgDef arg;
                    int count = 0;
                    for(int k = 0; k < tempMethod.argList.size(); k++){
                        arg = tempMethod.argList.get(k);
                        if( arg.argDirection.equals("out")){
                            output += generateSetMsgArg("args[" + count + "]",
                                                        arg.getArgType(),
                                                        arg.getArgName(),
                                                        1);
                            count++;
                        }
                    }
                    output += indentDepth;
                    output += "status = MethodReply(msg, args, "
                        + tempMethod.outArgCount
                        + ");\n\n"
                        + indentDepth;
                    output += "return status;\n} /* "
                        + tempMethod.getName()
                        + "MethodReply() */";
                    output += "\n\n";
                    writeCode(output);
                    output = "";
                }
        }
    }
    
    /**
     * Writes the functions that send the signal.
     */
    private void writeSignals(){
        ArrayList<SignalDef> sigList;
        SignalDef tempSignal;
        String sigName;
        String output = "";

            sigList = inter.getSignals();
            for(int j = 0; j < sigList.size(); j++){
                tempSignal = sigList.get(j);
                sigName = tempSignal.getName();
                output += topBracket
                    + "  METHOD: Send"
                    + sigName
                    + "()\n";
                output += "  Sends out the \""
                    + sigName
                    + "\" signal with the specified arguments.\n";
                output += "  Leave the destination field as NULL for"
                    + " broadcast or set it as the\n";
                output += "  desired recipient's unique or well-known name.\n";
                output += bottomBracket;

                if(tempSignal.argList.isEmpty()){
                    output += "QStatus "
                        + objName
                        + "::Send"
                        + sigName
                        + "(const char* destination){\n"
                        + indentDepth;
                }else{
                    output += "QStatus "
                        + objName
                        + "::Send"
                        + sigName
                        + "("
                        + generateArgs(tempSignal.argList)
                        + ", const char* destination){\n"
                        + indentDepth;
                }
                output += "/* get the interfaceDescription member of the "
                    + sigName
                    + " signal */\n"
                    + indentDepth;
                output += "const InterfaceDescription* iface = myBusAttachment->"
                    + "GetInterface(\""
                    + WriteCode.getInterfaceBySignal(sigName)
                    + "\");\n"
                    + indentDepth;
                output += "const InterfaceDescription::Member* "
                    + "my_signal_member = iface->GetMember(\""
                    + sigName
                    + "\");\n"
                    + indentDepth;

                output += "QStatus status = ER_OK;\n\n"
                    + indentDepth;
                if(tempSignal.argList.isEmpty()){
                    output += "/* send the signal */\n"
                        + indentDepth;
                    if(tempSignal.isSecure){
                        output += "/* This signal is encrypted because the"
                            + " signal is marked as secure in the XML */\n"
                            + indentDepth;
                        output += "status = Signal(destination, "
                            + "myBusListener->mySessionID, "
                            + "*my_signal_member, NULL, 0, "
                            + "ALLJOYN_FLAG_ENCRYPTED);\n"
                            + indentDepth;
                    }else{
                        output += "status = Signal(destination, "
                            + "myBusListener->mySessionID, "
                            + "*my_signal_member, NULL, 0, 0);\n"
                            + indentDepth;
                    }
                }else{
                    output += "/* pack the arguments into MsgArg */\n"
                        + indentDepth;
                    output += "MsgArg args["
                        + tempSignal.argList.size()
                        + "];\n";

                    ArgDef arg;
                    int count = 0;
                    for(int k = 0; k < tempSignal.argList.size(); k++){
                        arg = tempSignal.argList.get(k);
                        if(arg.getArgDirection().equals("in")){
                            output += generateSetMsgArg("args[" + count + "]",
                                                        arg.getArgType(),
                                                        arg.getArgName(),
                                                        1);
                            count++;
                        }
                    }
                    output += indentDepth;
                    output += "/* send the signal */\n"
                        + indentDepth;
                    if(tempSignal.isSecure){
                        output += "/* This signal is encrypted because"
                            + " the signal is marked as secure in the XML */\n"
                            + indentDepth;
                        output += "status = Signal(destination, "
                            + "myBusListener->mySessionID, "
                            + "*my_signal_member, args, "
                            + tempSignal.argList.size()
                            + ", 0, ALLJOYN_FLAG_ENCRYPTED);\n"
                            + indentDepth;
                    }else{
                        output += "status = Signal(destination, "
                            + "myBusListener->mySessionID, "
                            + "*my_signal_member, args, "
                            + tempSignal.argList.size()
                            + ", 0, 0);\n"
                            + indentDepth;
                    }
                }
                output += "return status;\n";

                output += "} /* Send"
                    + sigName
                    + "() */\n\n";
                writeCode(output);
                output = "";
        }
        writeCode(output);
     
    }

    
    /**
     * Writes the Get and Set methods that are used to implement the properties
     * interface.
     */
    private void writeGetSet(){
        String output = "";
        PropertyDef tempProp;

            for(int j = 0; j < inter.getProperties().size(); j++){
                tempProp = inter.getProperties().get(j);
                output += "else if(0 == strcmp(\""
                    + tempProp.getName()
                    + "\", propName)){\n";

                if(tempProp.getAccess().equals("write")){
                    output += "status = ER_BUS_PROPERTY_ACCESS_DENIED;\n";
                } else{
                    output += generateSetMsgArg("val",
                                                tempProp.getSignature(),
                                                tempProp.getName(),
                                                2);
                }
                output += indentDepth
                    + "}";
            }
if(!output.equals("") && writeGetMethod){
            output = topBracket
                + "  METHOD: Get()\n"

                + "  Overwrites the Get method of the BusObject class to"
                + " support the properties\n"
                + "  specified in the interfaces. Handles a GetProperty call.\n"
                + bottomBracket
                + "QStatus "
                + objName
                + "::Get(const char *ifcName, const char *propName,"
                + " MsgArg& val){\n"
                + indentDepth
                + "QStatus status = ER_OK;\n"
                + indentDepth
                + "/* Check the requested property and return the value if"
                + " it exists. */\n"
                + indentDepth
                + output.substring(5);

            output += "else{\n"
                + indentDepth
                + indentDepth;
            output += "/* if the requested property doesn't exist, return"
                + " error. */\n"
                + indentDepth
                + indentDepth;
            output += "status = ER_BUS_NO_SUCH_PROPERTY;\n"
                + indentDepth;
            output += "}\n"
                + indentDepth
                + "return status;\n} /* Get() */\n\n";
            writeCode(output);
            output = "";
        }
for(int j = 0; j < inter.getProperties().size(); j++){
                tempProp = inter.getProperties().get(j);
                if(tempProp.getAccess().equals("read")){
                    output += "else if(0 == strcmp(\""
                        + tempProp.getName()
                        + "\", propName)){\n";
                    output += indentDepth
                        + indentDepth
                        + "status = ER_BUS_PROPERTY_ACCESS_DENIED;\n";
                }else{
                    output += "else if((0 == strcmp(\""
                        + tempProp.getName()
                        + "\", propName)) && (val.typeId == ";
                    if(isArgContainerType(tempProp.getArg())){
                        if (tempProp.getSignature().charAt(0) == '(')
                            output += "ALLJOYN_STRUCT";
                        else if (tempProp.getSignature().charAt(0) == 'a'){
                            output += mapALLJOYNArrayType(tempProp.getSignature().charAt(1));
                        } else if (tempProp.getSignature().charAt(0) == 'v'){
                            output += "ALLJOYN_VARIANT";
                        }
                    } else{
                        output += mapALLJOYNtypeId(tempProp.getSignature().charAt(0));
                    }
                    output += ")){\n";
                    output += generateGetMsgArg("val",
                                                tempProp.getSignature(),
                                                tempProp.getName(),
                                                2);
                }
                output += indentDepth
                    +"}";
            }
        if(!output.equals("") && writeSetMethod){
            output = topBracket
                + "  METHOD: Set()\n"

                + "  Overwrites the Set method of the BusObject class to"
                + " support the properties\n"
                + "  specified in the interfaces. Handles a SetProperty call.\n"
                + bottomBracket
                + "QStatus "
                + objName
                + "::Set(const char *ifcName, const char *propName,"
                + " MsgArg& val){\n"
                + indentDepth
                + "QStatus status = ER_OK;\n"
                + indentDepth
                + "/* Check the requested property and set the value if it"
                + " exists. */\n"
                + indentDepth
                + output.substring(5);

            output += "else{\n"
                + indentDepth
                + indentDepth;
            output += "/* if the requested property doesn't exist, return"
                + " error. */\n"
                + indentDepth
                + indentDepth;
            output += "status = ER_BUS_NO_SUCH_PROPERTY;\n"
                + indentDepth;
            output += "}\n"
                + indentDepth
                + "return status;\n} /* Set() */\n\n";
            writeCode(output);
            output = "";
        }
    }
    
    /**
     * Writes the ALLJOYN AddMethodHandler() calls for the service constructor.
     * @param interfaceDesc the interface for which the ALLJOYN
     * AddMethodHander() calls will be printed.
     * @param varName the name of the ALLJOYN interfaceDescription variable
     * name.
     */
    private String generateAddMethods(InterfaceDescription interfaceDesc,
                                      String varName){
        MethodDef tempMethod;
        String output = "";
        for(int j = 0; j < interfaceDesc.getMethods().size(); j++){
            tempMethod = interfaceDesc.getMethods().get(j);
            output += indentDepth
                + "AddMethodHandler("
                + varName
                + "->GetMember(\""
                + tempMethod.getName()
                + "\"),\n"
                + indentDepth
                + indentDepth
                + indentDepth
                + indentDepth
                + indentDepth;
            output += "static_cast<MessageReceiver::MethodHandler>(&"
                + objName
                + "::"
                + tempMethod.getName()
                + "Handler));\n\n"
                + indentDepth;
        }
        return output;
    }
    /**
     * Creates a string of all the arguments with their types for method
     * definitions
     * @param argList
     * @param outCount, the number of output arguments
     * @return the generated arguments in a string.
     */
    private String generateMethodReplyArgs(ArrayList<ArgDef> argList,
                                           int outCount){
    	String output = "";
    	ArgDef tempArg;
        int j = outCount;

        for(int i = 0; i < argList.size(); i++){
            tempArg = argList.get(i);
            if(tempArg.getArgDirection().equals("out")){
                output += parseInArg(tempArg);
                j--;
                if(j != 0){
                    output += ", ";
                }
            }
        }
    	return output;
    }
    
    protected String generateMethodWrapperArgs(ArrayList<ArgDef> argList){
    	String result = "";
        ArgDef arg;
        String type;
        char c;

        for(int i = 0; i < argList.size(); i++){
            arg = argList.get(i);
            type = arg.getArgType();
            c = type.charAt(0);
            if(arg.getArgDirection().equals("in")){
                if(isArgContainerType(arg)){
                    if(c == 'a'){
                        if(dictEntryArgList.contains(arg)){
                            result += arg.getArgName();
                            result += ", "
                                + arg.getArgName()
                                + "NumElements";
                        }else if(type.charAt(1) == '('){
                            result += arg.getArgName() +
                                ", " +
                                arg.getArgName()+"NumElements";
                        }else if(type.charAt(1) == 'a'){
                            result += "args["+i+"]";
                        }else if(type.charAt(1) == 's' ||
                                 type.charAt(1) == 'o' ||
                                 type.charAt(1) == 'g'){
                            result += arg.getArgName();
                            result += ", "
                                + arg.getArgName()
                                + "NumElements";
                        }else{
                            result += "args["
                                + i
                                + "].v_scalarArray."
                                + mapALLJOYNMsgType(type.charAt(1));
                            result += ", args["
                                + i
                                + "].v_scalarArray.numElements";

                            //Bool array needs a type cast
                            if(type.equals("ab")){
                                result = "(const bool*)" + result;
                            }

                            //Signed Long array needs a type cast
                            if(type.equals("ax")){
                                result = "(const signed long long*)" + result;
                            }
                            //Unsigned Long array needs a type cast
                            if(type.equals("at")){
                                result = "(const unsigned long long*)" + result;
                            }
                        }
                    }else if(c == '{'){
                    } else if(c == '('){
                        result += arg.getArgName();
                    } else {
                        result += "args["
                            + i
                            + "]";
                    }
                }else{
                    result += "args["
                        + i
                        + "]."
                        + mapALLJOYNMsgType(c);
                }
            }else{
                result += arg.getArgName();
                if(c == 'a' && type.charAt(1) != 'a'){
                    result += ", "
                        + arg.getArgName()
                        + "NumElements";
                }
            }

            if(i != argList.size() -1){
                result += ", ";
            }
        }
    	return result;
    }
    
    /**
     * Write the file description for the service header file.
     */
    private void writeHeaderComments(){
        String commentStr = licenseTextOnly();
        commentStr +=  fileName
            + "\nThis file defines the class "
            + objName
            + ".\nThis class extends the BusObject class and creates a"
            + " BusObject that acts as a service that handles incoming"
            + " method calls and has the ability to send out signals. This"
            + " class contains more user friendly methods that hides some"
            + " of the AllJoyn API from the user of this class.";

        String output = FormatCode.blockComment(commentStr);
        writeCode(output);
    }

    /**
     * Write the file description for the serviceMethods.cc file.
     */
    private void writeDevComments(){
        String commentStr = licenseTextOnly();
        commentStr += fileName
            + "\nThis file contains empty method handlers where the"
            + " developer can fill in his/her own implementations.";

        String output = FormatCode.blockComment(commentStr);
        writeCode(output);
    }
    
    /**
     * Write the file description for the service.cc file
     */
    private void writeCCComments(){
        String commentStr = licenseTextOnly();
        commentStr += fileName
            + "\nThis file contains the implementation of the "
            + objName
            + " class, which extends the BusObject class from AllJoyn library."
            + " Most of these methods deal with the alljoyn API to make it"
            + " easier for the developer to use.";

        String output = FormatCode.blockComment(commentStr);
        writeCode(output);
    }

    /**
     * Write the file description for the serviceMain.cc file
     */
    private void writeMainComments(){
        String commentStr = licenseTextOnly();
        commentStr += fileName
            + "\nSample implementation of an alljoyn service."
            + " This sample shows how to set up an alljoyn service that will"
            + " register with the well known name: '"
            + config.wellKnownName
            + "'. The service keeps running until it is terminated externally"
            + " or via an alljoyn call.";

        String output = FormatCode.blockComment(commentStr);
        writeCode(output);
    }
} // class ServiceCodeWriter
