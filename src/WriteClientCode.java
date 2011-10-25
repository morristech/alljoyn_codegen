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

import java.util.ArrayList;

/**
 * Child class of CodeWriter
 * 
 * Only one instance should be instantiated to create/generate all the client
 * files.  Contains methods that create and write the client files and helper
 * methods specific to the client files.
 */
class WriteClientCode extends WriteCode{
    public WriteClientCode() {
        if(ParseAJXML.useFullNames) {
            objName = inter.className;
        }
        else {
            objName = inter.getName();
        }
        objName += "Client";
    }

    /**
     * Creates the header file and write the generated client class definition
     * in it.
     * 
     * @see #writeHeaderIncludes(boolean, boolean)
     * @see #writeClassDef()
     * @see #writeIfNDef()
     */
    public void writeHeaderFile(){
        fileName = objName + ".h";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN "
                               + fileName
                               + "!!! EXITING", 0);
        }

        
        writeIfNDef();
        writeHeaderComment();
        writeHeaderIncludes(false, false);
        writeStructs();
        writeClassDef();
        
        closeFile();
    }
	
    /**
     * Creates the client cc file which contains most of the generated
     * functions that hides the alljoyn API from the developer.
     * 
     * @see #writeConstructor()
     * @see #writeMethodWrappers()
     * @see #writeSignalHandlerWrappers()
     * @see #writeRegisterSignal()
     */
    public void writeCCFile(){
        fileName = objName + ".cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN "
                               + fileName
                               + "!!! EXITING", 0);
        }
        
        writeCCComent();
        writeCCIncludes();
        writeConstructor();
        writeMethodWrappers();
        writeSignalHandlerWrappers();
        writeRegisterSignal();
        writeDiscoveryMethods();
        
        closeFile();
    }
	
    /**
     * Creates the clientHandlers.cc file and writes the empty signal handlers
     * where the developer can fill in with their implementation.
     * 
     * @see #writeCCIncludes()
     * @see #writeSignalHandlers()
     */
    public void writeDevCCFile(){
        fileName = objName + "Handlers.cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN "
                               + fileName
                               + "!!! EXITING", 0);
        }
        writeDevComment();
        writeCCIncludes();
        writeSignalHandlers();
        
        closeFile();
    }

    /**
     * Creates and writes the sample Main file which contains a simple client
     * implementation.
     */
    public void writeMainFile(){
        fileName = "ClientMain.cc";
        if (!openFile()) {
            UIOutput.LogFatal("COULD NOT OPEN "
                               + fileName
                               + "!!! EXITING");
        }
        
        writeMainComments();
        writeHeaderIncludes(false, true);
        writeMainFunction();
        
        closeFile();
    }

    /**
     * Writes the client main function to the client main file.
     */
    private void writeMainFunction(){
        String output = "";
        InterfaceDescription inter;
        SignalDef tempSig;		
        String commentStr;
        String code;
        commentStr = 
            "Static top level message bus manager - this is a static global in " +
            "this file as it needs to be accessible in both main() and the signal " +
            "handler for SIGINT.";
        output += FormatCode.blockComment(commentStr);    
        output += "static BusAttachmentMgr* AllJoynMgr = NULL;\n\n";
        
        commentStr = 
            "This is the handler for the Int signal (i.e. Ctrl+C). Without the " +
            "the signal handler the program will exit without stopping the bus " +
            "which may result in a memory leak.";
        output += FormatCode.blockComment(commentStr);
        output += "static void SigIntHandler(int sig){\n";
        output += FormatCode.indentln("AllJoynMgr->Delete();", 1);
        output += FormatCode.indentln("exit(0);", 1);
        output += "} /* SigIntHandler() */\n\n";
		
        commentStr = 
            "main()\n" +
            "The entry point for the executable.";
        output += FormatCode.blockComment(commentStr);
        
        output += "int main(int argc, char **argv, char**envArg){\n"; 
        output += FormatCode.indentln("QStatus status = ER_OK;", 1); 
        output += FormatCode.indentln("printf(\"AllJoyn Library version: %s\\n\"," +
                                      " ajn::GetVersion());", 1); 
        output += FormatCode.indentln("printf(\"AllJoyn Library build info: %s\\n\","
                                      + " ajn::GetBuildInfo());\n", 1); 
        output += FormatCode.comment("Install SIGINT handler so Ctrl + C deallocates"
                                     + " the memory properly", 1); 
        output += FormatCode.indentln("signal(SIGINT, SigIntHandler);\n", 1); 
        commentStr = "Create the bus attachment manager that handles the " +
            "interactions with the message bus.\n" +
            "The second argument is a boolean indicating whether remote " +
            "device discovery is enabled.";
        output += FormatCode.blockComment(commentStr, 1);

        code = String.format("AllJoynMgr = new BusAttachmentMgr(\"%sApp\", \"%s\", true);\n", 
        		config.className,
                config.wellKnownName);
        output += FormatCode.indentln(code, 1); 
        writeCode(output);
        
        output = "";
		
        for(ObjectData obj : config.objects) {
            commentStr = String.format(
                "Create a %1$s object using the BusAttachment owned by the bus " +
                "manager and preferred path.  The %1$s object allows the developer " +
                "to make method calls to the corresponding service at the " +
                "specified path or receive signals from it.",
                obj.objName);
            output += FormatCode.blockComment(commentStr, 1);
            String className = "";
            if(ParseAJXML.useFullNames) {
                className = obj.inter.className;
            }
            else {
                className = obj.inter.getName();
            }
            className += "Client";
            code = String.format("%s %s(*AllJoynMgr->GetBusAttachment(),", 
                        className,
            		obj.objName);
            output += FormatCode.indentln(code, 1);
            code = "*AllJoynMgr->GetBusListener(),";
            output += FormatCode.indentln(code, 2);  
            code = String.format("\"%s\",", config.wellKnownName);
            output += FormatCode.indentln(code, 2);                        
            code = String.format("\"%s\");\n", obj.objPath);
            output += FormatCode.indentln(code, 2);
        }

        output += FormatCode.comment("Start the BusAttachment for the client", 1); 
        output += FormatCode.indentln("AllJoynMgr->StartClient();\n", 1);

        code = String.format("status = %s.FindName();", config.objects.get(0).objName);
        output += FormatCode.indentln(code, 1);
        output += FormatCode.indentln("if (ER_OK != status){", 1);
        output += FormatCode.indentln("printf(\"%s.FindName failed\\n\", ajn::org::alljoyn::Bus::InterfaceName);", 2); 
        output += FormatCode.indentln("}", 1);
        output += FormatCode.indentln("else{", 1);
        commentStr = "It may take a while for a remote service to be found. " +
        	"this loop checks to see if a remote service has been found. " +
        	"After waiting for a short while the code will try and " +
        	"connect with a local service. If you only want to connect " +
        	"to remote services use a while loop.  If you only want to " +
        	"connect to local services change the third argument when " +
        	"calling the BusAttachmentMgr to false.\n" +
        	"Modify this loop to increase or decrease the wait time.";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("for(int i = 0; i < 100; i++){", 2);
        code = String.format("if(!%s.NameFound()){", config.objects.get(0).objName);
        output += FormatCode.indentln(code, 3);
        output += FormatCode.indentln("usleep(100);", 4);
        output += FormatCode.indentln("}",3);
        output += FormatCode.indentln("}",2);
        output += FormatCode.indentln("}\n",1);

        for(ObjectData obj : config.objects) {
            output += FormatCode.comment("Set up the Proxy Bus Object", 1);
            if(config.runnable) {
                code = String.format("printf(\"Registering %s at %s\\n\");",
                                     obj.objName, obj.objPath);
                output += FormatCode.indentln(code, 1);
            }
            code = String.format("status = %s.SetUpProxy();", obj.objName);
            output += FormatCode.indentln(code, 1);
            output += FormatCode.indentln("if(status != ER_OK){", 1);
            code = String.format("printf(\"%s", obj.objName);
            code += ".SetUpProxy() failed: %s\\n\", QCC_StatusText(status));";
            output += FormatCode.indentln(code, 2); 
            output += FormatCode.indentln("AllJoynMgr->Delete();", 2); 
            output += FormatCode.indentln("return (int) status;", 2); 
            output += FormatCode.indentln("}\n", 1);
        }

        for(ObjectData obj : config.objects) {
          	for(int j = 0; j < obj.inter.getSignals().size(); j++){
        		tempSig = obj.inter.getSignals().get(j);
        		commentStr = String.format(
        				"registers the handler for \"%s\" signal, comment out " +
        				"this call if you want to ignore the signal",
        				tempSig.getName());
        		output += FormatCode.comment(commentStr, 1);
        		output += String.format("%s%s.Register%sHandler();\n\n",
        				FormatCode.indent(1),
        				obj.objName,
        				tempSig.getName());
        	}
        }
	
        if(config.runnable){
            commentStr = "Below is the code generated by the -R flag.";
            output += FormatCode.blockComment(commentStr, 1);
        }
        else{
            commentStr = "Add the code for using the client object here.";
            output += FormatCode.blockComment(commentStr, 1);
        }
        /*
         * if runnable flag is set, generate runnable code that calls the
         * methods, etc
         */
        if(config.runnable){
            for(ObjectData obj : config.objects) {
                output += GenerateRunnableCode.generateClientMethodCalls(obj.inter.getMethods(),
                                                                         obj.objName);
                
                output += "\n";
                if(!obj.inter.isDerived) {
                    output += GenerateRunnableCode.generateClientProperties(obj.inter.getProperties(),
                                                                        obj.inter.getFullName(),
                                                                        obj.objName);
                }
                else {
                    for(InterfaceDescription parent : obj.inter.parents) {
                        output += GenerateRunnableCode.generateClientProperties(parent.getProperties(),
                                                                        parent.getFullName(),
                                                                        obj.objName);
                    }
                }
            }
        }

        output += "\n";
        output += FormatCode.indentln("fflush(stdout);\n", 1);
        output += FormatCode.comment("Stop and deallocate the BusAttachment", 1);
        output += FormatCode.indentln("AllJoynMgr->Delete();\n", 1); 
        output += FormatCode.indentln("return (int) status;", 1);
        output += "} /* main() */\n";
        writeCode(output);
    }

    /**
     * Writes the client object class definition. Should be called by client
     * header file.
     */
    private void writeClassDef() {
    	String output = "";
        output += "\n";
    	String commentStr;
    	String code;
    	commentStr = String.format(
            "CLASS: %s\n" +
            "This class is a child of the BusObject class. This is " +
            "required so that it can interact directly with the bus.",
            objName);
    	output += FormatCode.blockComment(commentStr);
    	output +=String.format("class %s : public BusObject\n{\n", 
                               objName);
        output += FormatCode.indentln("public:", 1); 
        commentStr = String.format("METHOD: %s()\n" +
                                   "Constructor for the %s class. Takes in a pointer to the " +
                                   "BusAttachment. It will connect with the desired path for the " +
                                   "object you are trying to connect with.", 
                                   objName,
                                   objName);
        output += FormatCode.blockComment(commentStr, 2);
        
        code = String.format(
            "%s(BusAttachment &bus, MyBusListener &busListener, " +
            "const char *endpoint, const char* path);\n",
            objName);
        output += FormatCode.indentln(code, 2);
        ArrayList<MethodDef> tempMethodList;
        ArrayList<SignalDef> tempSignalList;
        MethodDef tempMethod;
        SignalDef tempSignal;
        
        writeCode(output);
        output = "";

        //print out the wrapped methods for each method in each interface
            tempMethodList = inter.methods;
/*        if(inter.isDerived) {
            for(InterfaceDescription parent : inter.parents) {
                tempMethodList.addAll(parent.getMethods());
            }
        }
*/
        	
            for(int j = 0; j < tempMethodList.size(); j++){
                tempMethod = tempMethodList.get(j);
                String methodName = tempMethod.getName();
                commentStr = String.format(
                    "METHOD %1$s()\n" +
                    "This method takes the input arguments and makes an " +
                    "AllJoyn method call for the \"%1$s\" method to the " +
                    "BusObject at the specified path. If the \"%1$s\" " +
                    "method is synchronous, the output values will be " +
                    "stored at the pointers' locations that were passed in.",
                    methodName);
                output += FormatCode.blockComment(commentStr, 2);

                if(tempMethod.argList.isEmpty()){
                    code = String.format(
                        "QStatus %s();\n", 
                        methodName);
                    output += FormatCode.indentln(code, 2);
                }else{
                    code = String.format(
                    	"QStatus %s(%s);\n", 
                        methodName,
                        generateArgs(tempMethod.argList));
                    output += FormatCode.indentln(code, 2);
                }        			
            }
            writeCode(output);
            output = "";
        
        // print out RegisterSignalHandler for each signal in each interface
            tempSignalList = inter.signals;
        	
            for(int j = 0; j < tempSignalList.size(); j++){
                tempSignal = tempSignalList.get(j);
                String signalName = tempSignal.getName();
                commentStr = String.format(
                    "METHOD: Register%1$sHandler()\n" +
                    "Start listening for the \"%1$s\" signal and register " +
                    "the \"%1$sHandler\" as the signal handler.\n" +
                    "DO NOT call this method if the developer doesn't " +
                    "want to receive that signal",
                    signalName);
                output += FormatCode.blockComment(commentStr, 2);
                output += indentDepth 
                    + indentDepth 
                    + "QStatus Register" 
                    + signalName 
                    + "Handler();\n\n"; 
            }

        commentStr = String.format("METHOD FindName()\n" +
    			"Look for a service advertising the \"%s\" well-known name", 
    			config.wellKnownName);
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("QStatus FindName();\n", 2);
        commentStr = "METHOD NameFound()\n" +
		"If the name requested in the \"FindName\" call has been found " +
		"this method will return true";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("bool NameFound();\n", 2);
        
        commentStr = "METHOD SetUpProxy()\n" +
		"If the client has joined a session, this method will create a " +
		"proxy bus object and set up the user defined interface with it " +
        "to prepare for making remote method calls";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("QStatus SetUpProxy();\n", 2);
        
        commentStr = 
            "MEMBER: proxyBusObj\n" +
            "Proxy bus object used to make remote method calls with a service.";
        output += FormatCode.blockComment(commentStr, 2);      
        output += FormatCode.indentln("ProxyBusObject* proxyBusObj;\n", 2);
        
        output += FormatCode.indentln( "private:", 1);
        //print out SignalWrapper and SignalHandler for each signal
            tempSignalList = inter.signals;
        	
            for(int j = 0; j < tempSignalList.size(); j++){
                tempSignal = tempSignalList.get(j);
                String signalName = tempSignal.getName();
                commentStr = String.format(
                    "METHOD: %sWrapper()\n" +
                    "This is the actual signal handler that is invocked by" +
                    "the AllJoyn API when the %s received the \"%s\" " +
                    "signal. This method then calls the developer's " +
                    "implementation of the signal handler after unpacking " +
                    "the arguments.", 
                    signalName,
                    objName,
                    signalName);
                output += FormatCode.blockComment(commentStr, 2);
                output += indentDepth 
                    + indentDepth 
                    + "void " 
                    + signalName 
                    + "Wrapper(const ajn::InterfaceDescription"
                    + "::Member* member,\n" 
                    + indentDepth 
                    + indentDepth 
                    + indentDepth;
                output += "const char* srcPath,\n" 
                    + indentDepth 
                    + indentDepth 
                    + indentDepth;
                output += "ajn::Message& msg);\n\n";
                commentStr = String.format(
                    "METHOD: %1$sHandler()\n" +
                    "This is an empty method where the developer should " +
                    "fill out his/her own implementation of the \"%1$s\" " +
                    "signal handler.\n" +
                    "No changes needed if the developer is not interested " +
                    "in that signal.",
                    signalName);
                output += FormatCode.blockComment(commentStr, 2);
                output += indentDepth 
                    + indentDepth 
                    + "void " 
                    + signalName 
                    + "Handler(" 
                    + generateArgs(tempSignal.argList) 
                    + ");\n\n";
            }
        
        //variables
        commentStr = 
            "MEMBER: myBusAttachment\n" +
            "Pointer to the busAttachment this service is registered with.";
        output += FormatCode.blockComment(commentStr, 2);
        output += FormatCode.indentln("BusAttachment* myBusAttachment;\n", 2);
        commentStr = 
            "MEMBER: busListener\n" +
            "Bus listener responsible for responding to \"FoundAdvertisedName\"" +
            " and \"NameOwnerChanged\" signals.";
        output += FormatCode.blockComment(commentStr, 2);       
        output += FormatCode.indentln("MyBusListener* myBusListener;\n", 2);
        commentStr = 
            "MEMBER: serviceName\n" +
            "Well-known service name used to create a proxy bus object.";
        output += FormatCode.blockComment(commentStr, 2);      
        output += FormatCode.indentln("const char* serviceName;\n", 2);
        
        output += "};\n\n"; 
        output += "#endif";
        writeCode(output);  
    }

    /**
     * Write the code for the empty signal handlers where developer can fill in
     * their own implementation to the file.
     */
    private void writeSignalHandlers(){
    	SignalDef tempSig;
        String output = "";
        String commentStr;
            for(int j = 0; j < inter.signals.size(); j++){
                tempSig = inter.signals.get(j);
                commentStr = String.format(
                    "METHOD: %1$sHandler()\n" +
                    "This is an empty method where the developer should " +
                    "fill out his/her own implementation of the \"%1$s\" " +
                    "signal handler.\n" +
                    "No changes needed if the developer is not interested " +
                    "in that signal.", 
                    tempSig.getName());
                output += FormatCode.blockComment(commentStr);
                output += String.format("void %s::%sHandler(%s){\n", 
                		objName,
                		tempSig.getName(),
                		generateArgs(tempSig.argList));
                if(config.runnable){
                    output += GenerateRunnableCode.generateSignalHandler(tempSig);
                }else{
                    output += FormatCode.comment(
                    	"Fill in signal handler implementation here.", 1);
                }
                output += "}/*" 
                    + tempSig.getName() 
                    + "Handler */\n\n";
            }
        writeCode(output);
    }
    
    /**
     * Writes the client object constructor code to the file.
     */
    private void writeConstructor(){
        String output = "";
        String commentStr;
        String code;
        commentStr = String.format(
            "METHOD: %1$s()\n" +
            "Constructor for the %1$s class. Takes in pointer to the " +
            "BusAttachment it will connect with the desired path for the " +
            "object you are trying to connect with.",
            objName);
        output += FormatCode.blockComment(commentStr);
        
        output += objName 
            + "::" 
            + objName
            + "(BusAttachment &bus, MyBusListener &busListener, "
            + "const char *endpoint, const char* path)\n";
        
        output += FormatCode.indentln(": BusObject(bus, path, false)", 1);
        
        output += "{\n" 
            + indentDepth;
        output += "myBusAttachment = &bus;\n" 
            + indentDepth;
            
        output += "myBusListener = &busListener;\n" 
            + indentDepth;
        
        output += "serviceName = endpoint;\n" 
            + indentDepth;
            
        output += "proxyBusObj = NULL;";
        
        output += "\n";
        output += "} /* "
            + objName
            + "() */\n\n";
        writeCode(output);
    }
    
    /**
     * Writes the method wrappers for the client object class. The method
     * wrappers hide the alljoyn method call API from the developer.
     * 
     * These are the methods that should be called when the developer wants to
     * make a method call to the service bus object.
     */
    private void writeMethodWrappers(){
    	MethodDef temp;
    	ArrayList<MethodDef> methodList;
    	String ifaceFullName, name;
    	String output = "";
    	String commentString;
    	String code;
            methodList = inter.getMethods();
            ifaceFullName = inter.getFullName();
            for(int i = 0; i < methodList.size(); i++){
                temp = methodList.get(i);
                name = temp.getName(); 
                commentString = String.format(
                    "METHOD %1$s()\n" +
                    "This method takes the input arguments and makes a AllJoyn " +
                    "method call for the \"%1$s\" method to the BusObject at the " +
                    "specified path. If the \"%1$s\" method is synchronous, the " +
                    "output values will be stored at the pointers locations that " +
                    "were passed in.", 
                    name);
                output += FormatCode.blockComment(commentString, 0);
                if(temp.argList.isEmpty()){
                    output += String.format("QStatus %s::%s(){\n", 
                                            objName,
                                            name);
    			    
                }else{
                    output += String.format("QStatus %s::%s(%s){\n",
                                            objName,
                                            name,
                                            generateArgs(temp.argList));					
                }
                output += FormatCode.comment("Create a reply message that " +
                                             "will be used to store the method reply.", 1);
                output += FormatCode.indentln("Message replyMsg(*myBusAttachment);", 1);
                output += FormatCode.indentln("QStatus status = ER_OK;\n", 1);
                output += FormatCode.comment("The following code makes the " +
                                             "method call and returns the output.", 1);
                
                String methodCallCommentString = 
                    "Make the method call with the interface name, method " +
                    "name, MsgArg, and reply message.\n" +
                    "The arguments for making a method call:\n" +
                    "    interface name\n" +
                    "    method name\n" +
                    "    MsgArg array containing input arguments\n" +
                    "    Number of input arguments\n" +
                    "    reply message\n" +
                    "Note: There are several version of BusObject::MethodCall, " +
                    "this is one of them. Learn more about it in the AllJoyn " +
                    "API.";
                if(!temp.argList.isEmpty()){
                    output += FormatCode.indent(1) +
                        "/* Create the MsgArg with the input arguments */\n";
                    output += String.format("%sMsgArg args[%d];\n",
                                            FormatCode.indent(1),
                                            temp.inArgCount);
                    ArgDef arg;
                    for(int k = 0; k < temp.argList.size(); k++){
                        arg = temp.argList.get(k);
                        if(arg.getArgDirection().equals("in")){
                            //output += FormatCode.comment(String.format("%s \"in\" Arg", arg.getArgName()), 1);
                            output += generateSetMsgArg("args["+ k +"]", arg.getArgType(), arg.getArgName(), 1);
                        }
                    }
                    output += FormatCode.comment(methodCallCommentString, 1); 
                    if(temp.isSecure){
                        output += "\n" 
                            + indentDepth 
                            + "/* This method call is encrypted because the"
                            + " method is marked as secure in the XML */\n" 
                            + indentDepth;
                        output += "status = proxyBusObj->MethodCall(\"" 
                            + getInterfaceByMethod(name) 
                            + "\", \"" 
                            + name 
                            + "\", args, " 
                            + temp.inArgCount 
                            + ", replyMsg, DefaultCallTimeout,"
                            + " ALLJOYN_FLAG_ENCRYPTED);\n"; 
                    }else{
                        code = String.format(
                            "status = proxyBusObj->MethodCall(\"%s\", \"%s\", args, %d, replyMsg);",
                            getInterfaceByMethod(name),
                            name,
                            temp.inArgCount);
                        output += FormatCode.indentln(code, 1);
                    }
                }else{
                    output += FormatCode.comment(methodCallCommentString, 1); 
                    if(temp.isSecure){
                        output += "\n" 
                            + indentDepth 
                            + "/* This method call is encrypted because the"
                            + " method is marked as secure in the XML */\n" 
                            + indentDepth;
                        output += "status = proxyBusObj->MethodCall(\"" 
                            + getInterfaceByMethod(name)
                            + "\", \"" 
                            + name 
                            + "\", NULL, 0, replyMsg, DefaultCallTimeout,"
                            + " ALLJOYN_FLAG_ENCRYPTED);\n";    				
                    }else{
                        output += "status = proxyBusObj->MethodCall(\"" 
                            + getInterfaceByMethod(name)
                            + "\", \"" 
                            + name 
                            + "\", NULL, 0, replyMsg);\n"; 
                    }
                }
				
                output += FormatCode.indentln("if(status != ER_OK){", 1); 
                output += FormatCode.indentln("return status;", 2); 
                output += FormatCode.indentln("}\n", 1); 
				
                if(!temp.getRetType().equals("NULL")){
                    output += FormatCode.comment("The following code extracts the output"  
                                                 + " of the method call and returns them.", 1); 
                    output += FormatCode.indentln("const ajn::MsgArg* returnArgs;", 1); 
                    output += FormatCode.indentln("size_t numArgs;", 1); 
                    output += FormatCode.comment("Extract output arguments from the reply"
                                                 + " message.", 1); 
                    output += FormatCode.indentln("replyMsg->GetArgs(numArgs, returnArgs);", 1); 
                    output += FormatCode.indentln(String.format("if(numArgs == %d){", 
                                                                temp.outArgCount), 
                                                  1);
                    output += FormatCode.comment(
                        "if the number of output arguments is correct, set " +
                        "the output arguments to the output variables", 2); 

                    ArgDef arg;
                    int currentOutArg = 0;
                    for(int k = 0; k < temp.argList.size(); k++){
                        arg = temp.argList.get(k);
                        if(arg.getArgDirection().equals("out")){
                            
                            output += generateGetMsgArg("returnArgs["+ currentOutArg +"]", arg.getArgType(), arg.getArgName(), 2);
                            currentOutArg++;
                        }
                    }					
                    
                    output += FormatCode.indentln("return status;",2);
                    output += FormatCode.indentln("}else{", 1); 
                    output += FormatCode.indentln("return ER_BUS_BAD_VALUE;", 2); 
                    output += FormatCode.indentln("}", 1);

                }else{
                    output += FormatCode.indentln("return status;", 1);
                }
                output += String.format("} /* %s() */\n\n", temp.getName()); 
            }
    	writeCode(output);
    }

    private String getInterfaceByMethod(String methodName) {
        if(!inter.isDerived) {
            return inter.getFullName();
        }
        
        for(InterfaceDescription parent : inter.parents) {
            for(MethodDef method : parent.methods) {
                if(method.getName().equals(methodName)) {
                    return parent.getFullName();
                }
            }
        }

        return "";
    }
    
    /**
     * Writes the internal signal handlers which then call the developer's
     * signal handlers.
     */
    private void writeSignalHandlerWrappers(){
    	SignalDef tempSignal;
    	String output = "";
    	
            for(int j = 0; j < inter.signals.size(); j++){
                tempSignal = inter.signals.get(j);
                String commentStr = String.format(
                    "METHOD: %1$sWrapper()\n" +
                    "This is the actual signal handler that is invoked by the " +
                    "AllJoyn API when the objName receives the \"%1$s\" signal. " +
                    "This method then calls the developer's implementation of " +
                    "the signal handler after unpacking the arguments.",
                    tempSignal.getName()); 
                output += FormatCode.blockComment(commentStr);    
                output += String.format("void %s::%sWrapper(" +
                                        "const ajn::InterfaceDescription::Member* member, " +
                                        "const char *srcPath, " +
                                        "ajn::Message& msg){\n",
                                        objName,
                                        tempSignal.getName()); 
                if(!tempSignal.argList.isEmpty()){
                    output += FormatCode.comment("Get the arguments from the signal.", 1); 
                    output += FormatCode.indentln("const ajn::MsgArg* args;", 1); 
                    output += FormatCode.indentln("size_t numArgs;", 1); 
                    output += FormatCode.indentln("msg->GetArgs(numArgs, args);", 1); 
                    output += FormatCode.comment("if the number of arguments is correct,"
                                                 + " call the developer's signal handler", 1); 
                    output += FormatCode.indentln(
                        String.format("if(numArgs == %d){", tempSignal.argList.size()), 1); 

                    ArgDef arg;
                    int count = 0;
                    
                    for(int k = 0; k < tempSignal.argList.size(); k++){
                        arg = tempSignal.argList.get(k);
                        if(arg.getArgDirection().equals("in")){
                            if(isSignatureContainerType(arg.getArgType())){
                                if (arg.getArgType().charAt(0) == '('){
                                    output += generateGetMsgArg
                                        ("args["+ count +"]", 
                                         arg.getArgType(), 
                                         arg.argName,
                                         true,
                                         2);
                                } else if(isBasicArrayContainerType(
                                          arg.getArgType()) &&
                                          (arg.getArgType().charAt(1) == 's' ||
                                           arg.getArgType().charAt(1) == 'o' ||
                                           arg.getArgType().charAt(1) == 'g')){ 
                                    output += generateGetMsgArg(
                                        "args["+ count +"]", 
                                        arg.getArgType(), 
                                        arg.argName,
                                        true,
                                        2);
                                } else if(arg.getArgType().charAt(0) == 'a'
                                          && arg.getArgType().charAt(1) == '{'){
                                    output += generateGetMsgArg(
                                        "args["+ count +"]", 
                                        arg.getArgType(), 
                                        arg.argName,
                                        true,
                                        2);
                                } else if(arg.getArgType().charAt(0) == 'a' && 
                                          arg.getArgType().charAt(1) == '('){
                                    output += generateGetMsgArg(
                                        "args["+count+"]",
                                        arg.getArgType(),
                                        arg.getArgName(),
                                        true,
                                        2);
                                }
                            }
                            count++;
                        }
                    }
                    
                    String wrapperArgs = generateSignalWrapperArgs(
                        tempSignal.argList,
                        "in",
                        tempSignal.argList.size());
                    output += FormatCode.indentln(
                        String.format("%sHandler(%s);",
                                      tempSignal.getName(),
                                      wrapperArgs), 
                        2);
                    output+= FormatCode.indentln("}", 1);
                }else{
                    output += "/* Get the arguments from the signal. */\n" 
                        + indentDepth;
                    output += tempSignal.getName() 
                        + "Handler();\n";
                }
                output += "} /* " 
                    + tempSignal.getName() 
                    + "Wrapper() */\n\n";
    				
    				
                writeCode(output);
                output = "";
            }
    }
    
    /**
     * Generates the arguments used by the signal wrappers when calling the
     * signal handlers.
     * @param argList the ArrayList containing the signal's arguments.
     * @return the arguments.
     */
    protected String generateSignalWrapperArgs(ArrayList<ArgDef> argList,
                                               String direction,
                                               int dirCount){
    	String result = "";
    	ArgDef arg;
    	String type;
    	int count = dirCount;
    	char c;
    	
    	for(int i = 0; i < argList.size(); i++){
            arg = argList.get(i);
            type = arg.getArgType();
            c = type.charAt(0);
            if(arg.getArgDirection().equals(direction)){
                count--;
                if(isArgContainerType(arg)){
                    if(c == '('){
                        result += arg.getArgName();
                    }else if(c == 'a' ){
                        if(dictEntryArgList.contains(arg)){
                            result += arg.getArgName();
                            result += ", " 
                                + arg.getArgName() 
                                + "NumElements";
                        }else if(type.charAt(1) == 's' ||
                                 type.charAt(1) == 'o' ||
                                 type.charAt(1) == 'g'){
                            result += arg.getArgName();
                            result += ", " 
                                + arg.getArgName() 
                                + "NumElements";
                        }else if(type.charAt(1) == 'a'){
                            result += "args[" + i + "]";
                        }else if(type.charAt(1) == '('){
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
                                
                            //Signed Long array needs a type cast
                            if(type.equals("ax")){
                                result = "(const signed long long*)" + result;
                            }
                            
                            //Unsigned Long array needs a type cast
                            if(type.equals("at")){
                                result = "(const unsigned long long*)" + result;
                            }
                        }
                    } else {
                        result += "args[" + i + "]";
                    }
                }else{
                    result += "args[" 
                        + i 
                        + "]." 
                        + mapALLJOYNMsgType(c);
                }

                if(count != 0){
                    result += ", ";
                }    			
            }
    	}
    	return result;
    }
    
    /**
     * Writes the functions that registers the signal handler for each signal.
     */
    private void writeRegisterSignal(){
    	ArrayList<SignalDef> tempSignals;
    	SignalDef tempSig;
    	String output = "";
    	String commentStr;
            tempSignals = inter.getSignals();
            for(int j = 0; j < tempSignals.size(); j++){
                tempSig = tempSignals.get(j);
                commentStr = String.format(
                    "METHOD: Register%1$sHandler()\n" +
                    "Start listening for the \"%1$s\" signal and register the " +
                    "\"%1$sHandler\" as the signal handler.\n" +
                    "DO NOT call this method if the developer does not want " +
                    "to receive that signal.",
                    tempSig.getName());
                output += FormatCode.blockComment(commentStr);
                output += "QStatus " 
                    + objName 
                    + "::Register" 
                    + tempSig.getName() 
                    + "Handler(){\n"; 
                output += FormatCode.indentln("QStatus status;", 1);
                commentStr = String.format(
                    "The following code adds a match rule to the object " +
                    "so it will listen for the \"%s\" signal and " +
                    "registers a signal handler for it.", 
                    tempSig.getName());
                output += FormatCode.comment(commentStr, 1); 
                output += FormatCode.indentln(
                    "const ProxyBusObject& dbusObj = " +
                    "myBusAttachment->GetDBusProxyObj();", 1); 
                commentStr =
                    "Make the method call to add the match rule for the signal.\n" +
                    "The options for adding a match rule:\n" +
                    "   type =      the message type, i.e. signal, method call, etc\n" +
                    "   interface = the name of the interface\n" +
                    "   member =    the name of the signal";
                output += FormatCode.comment(commentStr, 1);
                output += FormatCode.indent(1);
                output += "MsgArg arg(\"s\", \"type='signal',interface='" 
                    + WriteCode.getInterfaceBySignal(tempSig.getName()) 
                    + "',member='" 
                    + tempSig.getName() 
                    + "'\");\n" 
                    + indentDepth;
                output += "Message reply(*myBusAttachment);\n" 
                    + indentDepth;
                output += "status = dbusObj.MethodCall(\"org.freedesktop.DBus"
                    + "\", \"AddMatch\", &arg, 1, reply);\n\n";
    			
                output += FormatCode.comment(
                    "Register a signal handler for the signal.", 1); 
                output += FormatCode.indent(1);
                output += "const InterfaceDescription* iface = proxyBusObj->GetInterface(\"" 
                    + WriteCode.getInterfaceBySignal(tempSig.getName()) 
                    + "\");\n" 
                    + indentDepth;
                output += "status = myBusAttachment->RegisterSignalHandler("
                    + "this,\n";
                output += FormatCode.indent(6);
                output += "static_cast<ajn::MessageReceiver::"
                    + "SignalHandler>(&" 
                    + objName 
                    + "::" 
                    + tempSig.getName() 
                    + "Wrapper),\n"; 
                output += FormatCode.indent(6);
                output += "iface->GetMember(\"" 
                    + tempSig.getName() 
                    + "\"),\n";
                output += FormatCode.indent(6);
                output += "this->GetPath());\n" 
                    + indentDepth;
                output += "return status;\n} /* Register" 
                    + tempSig.getName() 
                    + "Handler() */\n\n";
                writeCode(output);
                output = "";
            }
    }
    
    /**
     * Write the file description for the client header file.
     */
    private void writeHeaderComment(){
    	String output = "";
    	
    	String commentStr = licenseTextOnly();
    	commentStr += String.format(
            "%s\n" +
            "This file defines the implementation of the %s class.\n" +
            "This class extends the ProxyBusObject class which acts as " +
            "the remote representation of a BusObject on the bus. The " +
            "ProxyBusObject can make method calls to the BusObject and " +
            "receive signals from the BusObject.\n" +
            "The %s class provides more user friendly methods that hides " +
            "some of the AllJoyn API to make it easier to interact with a" +
            "ProxyBusObject",
            fileName,
            objName,
            objName);
    	output += FormatCode.blockComment(commentStr);
    	writeCode(output);
    }
    
    /**
     * Write the file description for the clientHandler.cc file.
     */
    private void writeDevComment(){
    	String output = "";
    	String commentStr = licenseTextOnly();
    	commentStr += String.format(
            "%s\n" +
            "This file contains empty singnal handler where the developer " +
            "may choose to fill in his/her own signal handler implementation.",
            fileName);
    	output += FormatCode.blockComment(commentStr);    	
    	writeCode(output);
    }
    
    /**
     * Write the file description for the client.cc file.
     */
    private void writeCCComent(){
    	String output = "";
    	String commentStr = licenseTextOnly();
        commentStr += String.format(
            "%s\n" +
            "This file contains the implementation of the client class.",
            fileName);
    	output += FormatCode.blockComment(commentStr);
    	output += "\n";
    	writeCode(output);
    }
    
    /**
     * Write the file description for the clientMain.cc file.
     */
    private void writeMainComments(){
    	String output = "";
    	String commentStr = licenseTextOnly();
    	commentStr += String.format(
            "%s\n" +
            "Sample implementation of an AllJoyn clien. This sample shows " +
            "how to set up an AllJoyn client object that represents the " +
            "service object with the well-known name \"%s\" and make " +
            "method calls to the service and register signal handler for " +
            "the service's signals.",
            fileName,
            config.wellKnownName);
    	output += FormatCode.blockComment(commentStr);
    	writeCode(output);
    }
    
    /**
     * Write the FindName and Name Found Methods
     */
    private void writeDiscoveryMethods(){
    	String output = "";
    	String commentStr;
    	String code;
        String varName;
        
    	commentStr = String.format("METHOD FindName()\n" +
    			"Look for a service advertising the \"%s\" well-known name", 
    			config.wellKnownName);
    	output += FormatCode.blockComment(commentStr);
    	output += String.format("QStatus %s::FindName(){\n", objName);
    	output += FormatCode.indentln("QStatus status = ER_OK;", 1);
        
        code = String.format("status = myBusAttachment->FindAdvertisedName(\"%s\");",
                config.wellKnownName);
        output += FormatCode.indentln(code, 1);

    	output += FormatCode.indentln("return status;", 1);
    	output += "} /* FindName() */\n\n";
    	
    	commentStr = "METHOD NameFound()\n" +
    			"If the name requested in the \"FindName\" call has been found " +
    			"this method will return true";
    	output += FormatCode.blockComment(commentStr);
    	output += String.format("bool %s::NameFound(){\n", objName);

    	output += FormatCode.indentln("return myBusListener->nameFound;", 1);

    	output += "} /* NameFound() */\n\n";
        
        commentStr = "METHOD SetUpProxy()\n" +
		"If the client has joined a session, this method will create a " +
		"proxy bus object and set up the user defined interface with it " +
        "to prepare for making remote method calls";
        output += FormatCode.blockComment(commentStr);
        output += String.format("QStatus %s::SetUpProxy(){\n", objName); 
        output += FormatCode.indentln("QStatus status = ER_OK;\n", 1);
        commentStr = "If we have not joined a session yet, return an error.";
        output += FormatCode.comment(commentStr, 1);
        output += FormatCode.indentln("if(!NameFound()){", 1);
        output += FormatCode.indentln("status = ER_FAIL;", 2);
        code = String.format("printf(\"%s::SetUpProxy() - Session not joined yet\\n\");",
                objName);
        output += FormatCode.indentln(code, 2);
        output += FormatCode.indentln("return status;", 2);
        output += FormatCode.indentln("}\n", 1);
        
        commentStr = "Create a proxy bus object.";
        output += FormatCode.comment(commentStr, 1);
        code = "proxyBusObj = new ProxyBusObject(*myBusAttachment, serviceName, ";
        output += FormatCode.indentln(code, 1);
        code = "this->GetPath(), myBusListener->mySessionID);\n";
        output += FormatCode.indentln(code, 2);
      	
        commentStr = "The following code creates an interface and populates " +
            "it with the appropriate content from the interface " +
            "specified in the XML description.";
        output += FormatCode.comment(commentStr, 1);
        output += FormatCode.indent(1);
  
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
                + "getIface "
                + " = bus.GetInterface(\""
                + curInterface.getFullName()
                + "\""
                + ");\n"
                + indentDepth;
            output += "if(!getIface) {\n"
                + indentDepth
                + indentDepth;
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
                output += "status = bus.CreateInterface(\"" 
                    + curInterface.getFullName() 
                    + "\", " 
                    + "createIface"
                    + ", true);\n" 
                    + indentDepth;        		
            }else{
                output += "status = bus.CreateInterface(\"" 
                    + curInterface.getFullName() 
                    + "\", " 
                    + "createIface"
                    + ", false);\n" 
                    + indentDepth;
            }
            writeCode(output);
            output = "";
            this.writeInterfaceMember("createIface", curInterface);
            output += indentDepth
                + "createIface"
                + "->Activate();\n" 
                + indentDepth
                + indentDepth;
            output += "\n"; 
            commentStr =String.format( 
                "Add the interfaceDescription to the %s object so that it has " +
                "a definition of the interface.",
                objName);
            output += FormatCode.comment(commentStr, 2);
            output += FormatCode.indentln(String.format(
                           "proxyBusObj->AddInterface(*createIface);"), 2);
            output += indentDepth
                + "}\n"
                + indentDepth
                + "else {\n";
            output += FormatCode.indentln(String.format(
                           "proxyBusObj->AddInterface(*getIface);"), 2);
            output += indentDepth
                + "}\n";
        }
        output += FormatCode.indentln("return status;", 1);
        output += "} /* SetUpProxy() */";
        
    	writeCode(output);
    }
} // class ClientCodeWriter
