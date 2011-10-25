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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

/**
 * 
 * Data-structure class that stores configurations from cmd line and 
 * the XML data structure from the XML parser
 *
 */
public class CodeGenConfig {
    private static CodeGenConfig instance = null;
    public ArrayList<ObjectData> objects;
    public ArrayList<InterfaceDescription> interfaces;
    public AJNErrorHandler errorHandler;
    public String programName;
    public String objPath;
    public String className;
    public String outputPath;
    public String wellKnownName;
    public String cLicense;
    public String shLicense;
    public boolean overWrite;
    public boolean clientOnly;
    public boolean useStrict;
    public boolean allowEmpty;
    public boolean runnable;

    public static CodeGenConfig getInstance() {
        if(instance == null){
            instance = new CodeGenConfig();
        }

        return instance;
    }

    private CodeGenConfig() {
        // buffer to read the file name into - note the name will be truncated
        // to 64 characters.
        byte[] buffer = new byte[64];

        objects = new ArrayList<ObjectData>();
        interfaces = new ArrayList<InterfaceDescription>();
        errorHandler = null;
        objPath = "";
        className = null;
        outputPath = "";
        wellKnownName = "";
        overWrite = false;
        clientOnly = false;
        useStrict = true;
        allowEmpty = false;
        runnable = false;
        programName = getConfigFromFile("name");
        cLicense = getConfigFromFile("c-license");
        shLicense = getConfigFromFile("sh-license");
 
    } /* CodeGenData() */

    /**
     * Return the contents of a config file (in src/config) as a string.  this
     * is used to get the application name, and the license terms from a file
     * and turn them into Strings that can then be injected into generated
     * files.
     * @param file: the name of the file to return as a string.  It is assumed
     * that the file resides in the src/config directory of the JAR file.
     */
    private String getConfigFromFile(String file) {

        return ReadFiles.getStringFromJAR("src/config/"+file);
        
    } // getConfigFromFile() */

} // public class CodeGenData

/**
 * Interface description
 */
class InterfaceDescription {
    public String interfaceName;
    public String className;
    public String interfaceFullName;
    public boolean isSecure;
    public ArrayList<MethodDef> methods;
    public ArrayList<SignalDef> signals;
    public ArrayList<PropertyDef> properties;
    public ArrayList<InterfaceDescription> parents;
    public boolean isDerived;
    private LogOutput UIOutput;

    /**
     * Create a new interface
     */
    public InterfaceDescription() {
        isSecure = false;
        interfaceName = "";
        className = "";
        interfaceFullName = "";
        isDerived = false;
        methods = new ArrayList<MethodDef>();
        signals = new ArrayList<SignalDef>();
        properties = new ArrayList<PropertyDef>();
        parents = new ArrayList<InterfaceDescription>();
        UIOutput = new LogOutput("InterfaceDescription");
    }

    /**
     * Set the name of the interface
     * 
     * @param name
     *            the name of the interface
     */
    public void setName(String name) {
        int i = name.lastIndexOf('.');
        interfaceName = name.substring(i + 1);
        interfaceFullName = name;
        className = name.replaceAll("\\.", "_");
    }

    /**
     * Add a method to the interface description
     * @param method
     */
    public void addNewMethod(MethodDef method) {
        methods.add(method);
    }

    /**
     * Add a signal to the interface description
     * @param signal
     */
    public void addNewSignal(SignalDef signal) {
        signals.add(signal);
    }
    
    /**
     * Add a property to the interface description
     * @param prop
     */
    public void addNewProperty(PropertyDef prop){
        properties.add(prop);
    }


    /**
     * Return the name of the interface
     * 
     * @return the interface name
     */
    public String getName() {
        return interfaceName;
    }

    public String getClassName() {
        return className;
    }

    /**
     * @return The interface's full name in a string
     */
    public String getFullName() {
        return interfaceFullName;
    }

    /**
     * @return The ArrayList of parent class
     */
    public ArrayList<InterfaceDescription> getParents() {
        return parents;
    }

    /**
     * @return The ArrayList of MethoDefs for this interface
     */
    public ArrayList<MethodDef> getMethods() {
        return methods;
    }

    /**
     * 
     * @return The ArrayList of SignalDefs for this interface
     */
    public ArrayList<SignalDef> getSignals() {
        return signals;
    }
    
    /**
     * 
     * @return The ArrayList of PropertyDefs for this interface
     */
    public ArrayList<PropertyDef> getProperties(){
        return properties;
    }

    public void updateMemberNames() {
        HashMap<String, Integer> names = new HashMap<String, Integer>();
        for(MethodDef method : methods) {
            if(!names.containsKey(method.getName())) {
                names.put(method.getName(), new Integer(0));
            }
            names.put(method.getName(), new Integer(names.get(method.getName()).intValue() + 1));
        }
        for(SignalDef signal : signals) {
            if(!names.containsKey(signal.getName())) {
                names.put(signal.getName(), new Integer(0));
            }
            names.put(signal.getName(), new Integer(names.get(signal.getName()).intValue() + 1));
        }
        for(PropertyDef property : properties) {
            if(!names.containsKey(property.getName())) {
                names.put(property.getName(), new Integer(0));
            }
            names.put(property.getName(), new Integer(names.get(property.getName()).intValue() + 1));
        }

        for(Map.Entry<String, Integer> entry : names.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue().intValue();
            if(count > 1) {
                for(MethodDef method : methods) {
                    if(method.getName().equals(name)) {
                        method.setName(method.getInterfaceName().replaceAll("\\.", "_") + "_" + method.getName());
                    }
                } 
                for(SignalDef signal : signals) {
                    if(signal.getName().equals(name)) {
                        signal.setName(signal.getInterfaceName().replaceAll("\\.", "_") + "_" + signal.getName());
                    }
                }
                for(PropertyDef property : properties) {
                    if(property.getName().equals(name)) {
                        property.setName(property.getInterfaceName().replaceAll("\\.", "_") + "_" + property.getName());
                    }
                }
            }
        }
    }

    public boolean equals(Object obj) {
        if(obj instanceof InterfaceDescription) {
            InterfaceDescription other = (InterfaceDescription)obj;
            if(!interfaceName.equals(other.getName())) {
                return false;
            }
            if(!interfaceFullName.equals(other.getFullName())) {
                return false;
            }
            if(isSecure != other.isSecure) {
                return false;
            }
            Collections.sort(methods, new Comparator<MethodDef>() {
                public int compare(MethodDef one, MethodDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            Collections.sort(other.methods, new Comparator<MethodDef>() {
                public int compare(MethodDef one, MethodDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            if(!methods.equals(other.methods)) {
                return false;
            }

            Collections.sort(signals, new Comparator<SignalDef>() {
                public int compare(SignalDef one, SignalDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            Collections.sort(other.signals, new Comparator<SignalDef>() {
                public int compare(SignalDef one, SignalDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            if(!signals.equals(other.signals)) {
                return false;
            }

            Collections.sort(properties, new Comparator<PropertyDef>() {
                public int compare(PropertyDef one, PropertyDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            Collections.sort(other.properties, new Comparator<PropertyDef>() {
                public int compare(PropertyDef one, PropertyDef two){
                    return one.getName().compareTo(two.getName());
                }
            });
            if(!properties.equals(other.properties)) {
                return false;
            }

            return true;
        }

        return false;
    }

    /**
     * Prints out the interface's methods, signals, and properties for debug
     * purposes.
     */
    public String toString(){
        String output = "";
        MethodDef tempMeth;
        SignalDef tempSig;
        PropertyDef tempProp;
        
        UIOutput.LogInform("Interface name: " + interfaceFullName);
        for(int i = 0; i < methods.size(); i++){
            tempMeth = methods.get(i);
            UIOutput.LogInform("Method name: " + tempMeth.getName());
            UIOutput.LogInform("Input arg names: " +
			       tempMeth.getParamNames() +
			       "\t\t" +
			       "Input arg signature: " +
			       tempMeth.getParams() +
			       "\t\t" +
			       "Output arg names: " +
			       tempMeth.getRetNames() +
			       "\t\t" +
			       "Output arg signature: " +
			       tempMeth.getRetType() +
			       "\n");
        }
        
        for(int i = 0; i < signals.size(); i++){
            tempSig = signals.get(i);
            UIOutput.LogInform("Signal name : " + tempSig.getName());
            UIOutput.LogInform("Arg names: " + tempSig.getParamNames());
            UIOutput.LogInform("Arg signature: " + tempSig.getParams() + "\n");
        }
        
        for(int i = 0; i < properties.size(); i++){
            tempProp = properties.get(i);
            UIOutput.LogInform("Property name: " + tempProp.getName());
            UIOutput.LogInform("Property signature: " +
			       tempProp.getSignature());
            UIOutput.LogInform("Property access: " +
			       tempProp.getAccess() +
			       "\n");
        }

        return output;
    }
} // class InterfaceDescription

/**
 * Data class representing a method
 */
class MethodDef {
    private String type; 
    private String name; // method name
    private String params; // in signature
    private String return_type; // out signature
    private String param_names; // list of in names
    private String return_names; // list of out names
    private String interfaceName;
    public int inArgCount;
    public int outArgCount;
    public ArrayList<ArgDef> argList;
    public boolean noReply;    //no-reply annotation
    public boolean isSecure; //secure annotation

    public MethodDef() {
        type = "NULL";
        name = "NULL";
        interfaceName = "NULL";
        params = "NULL";
        return_type = "NULL";
        param_names = "NULL";
        return_names = "NULL";
        inArgCount = 0;
        outArgCount = 0;
        argList = new ArrayList<ArgDef>();
        noReply = false;
        isSecure = false;
    }
    
    public boolean hasArgName(String name){
        ArgDef arg;
        for(int i = 0; i < argList.size(); i++){
            arg = argList.get(i);
            if(arg.getArgName().equals(name)){
                return true;
            }
        }
        return false;
    }

    public void setRetNames(String outArgsNames) {
        return_names = outArgsNames;
    }

    public void setType(String t) {
        type = t;
    }

    public void setName(String n) {
        name = n;
    }

    public void setInterfaceName(String n) {
        interfaceName = n;
    }

    public void setParams(String p) {
        params = p;
    }

    public void setRetType(String rt) {
        return_type = rt;
    }

    public void setParamNames(String pn) {
        param_names = pn;
    }

    public String getRetNames() {
        return return_names;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getParams() {
        return params;
    }

    public String getRetType() {
        return return_type;
    }

    public String getParamNames() {
        return param_names;
    }

    public boolean equals(Object obj) {
        if(obj instanceof MethodDef) {
            MethodDef other = (MethodDef)obj;
            if(!type.equals(other.getType())){
                return false;
            }
            if(!name.equals(other.getName())){
                return false;
            }
            if(!interfaceName.equals(other.getInterfaceName())){
                return false;
            }
            if(!params.equals(other.getParams())){
                return false;
            }
            if(!return_type.equals(other.getRetType())){
                return false;
            }
            if(!param_names.equals(other.getParamNames())){
                return false;
            }
            if(!return_names.equals(other.getRetNames())){
                return false;
            }
            if(noReply != other.noReply){
                return false;
            }
            if(isSecure != other.isSecure){
                return false;
            }

            Collections.sort(argList, new Comparator<ArgDef>() {
                public int compare(ArgDef one, ArgDef two){
                    return one.getArgName().compareTo(two.getArgName());
                }
            });
            Collections.sort(other.argList, new Comparator<ArgDef>() {
                public int compare(ArgDef one, ArgDef two){
                    return one.getArgName().compareTo(two.getArgName());
                }
            });
            if(!argList.equals(other.argList)) {
                return false;
            }

            return true;
        }
        return false;
    }
} // class MethodDef

/**
 * Data class representing a property
 */
class PropertyDef{
    private String name;
    private String signature;
    private String access;
    private String interfaceName;
    private  ArgDef arg;
    
    public PropertyDef(){
        name = "NULL";
        signature = "NULL";
        interfaceName = "NULL";
        access = "NULL";
        arg = new ArgDef(null, null, "in", null); 
    }
    
    public void setName(String s){
        name = s;
        arg.argName = s;
    }

    public void setInterfaceName(String s){
        interfaceName = s;
    }
    
    public void setSignature(String s){
        signature = s;
        arg.argType = s;
    }
    
    public void setAccess(String s){
        access = s;
    }
    
    public String getName(){
        return name;
    }

    public String getInterfaceName() {
        return interfaceName;
    }
    
    public String getSignature(){
        return signature;
    }
    
    public String getAccess(){
        return access;
    }
    public ArgDef getArg(){
    	return arg;
    }

    public boolean equals(Object obj) {
        if(obj instanceof PropertyDef) {
            PropertyDef other = (PropertyDef)obj;
            if(!name.equals(other.getName())) {
                return false;
            }
            if(!signature.equals(other.getSignature())) {
                return false;
            }
            if(!access.equals(other.getAccess())) {
                return false;
            }
            if(!arg.equals(other.getArg())) {
                return false;
            }
            return true;
        }
        return false;
    }
} // class PropertyDef

/**
 * Data class representing a signal
 */
class SignalDef {
    private String type;
    private String name;
    private String params;
    private String return_type;
    private String param_names;
    private String interfaceName;
    public boolean isSecure;
    public ArrayList<ArgDef> argList;

    public SignalDef() {
        type = "NULL";
        name = "NULL";
        interfaceName = "NULL";
        params = "NULL";
        return_type = "NULL";
        param_names = "NULL";
        isSecure = false;
        argList = new ArrayList<ArgDef>();
    }
    
    public boolean hasArgName(String name){
        ArgDef arg;
        for(int i = 0; i < argList.size(); i++){
            arg = argList.get(i);
            if(arg.getArgName().equals(name)){
                return true;
            }
        }
        return false;
    }

    public void setType(String t) {
        type = t;
    }

    public void setName(String n) {
        name = n;
    }

    public void setInterfaceName(String n) {
        interfaceName = n;
    }

    public void setParams(String p) {
        params = p;
    }

    public void setRetType(String rt) {
        return_type = rt;
    }

    public void setParamNames(String pn) {
        param_names = pn;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getParams() {
        return params;
    }

    public String getRetType() {
        return return_type;
    }

    public String getParamNames() {
        return param_names;
    }

    public boolean equals(Object obj) {
        if(obj instanceof SignalDef) {
            SignalDef other = (SignalDef)obj;
            if(!type.equals(other.getType())) {
                return false;
            }
            if(!name.equals(other.getName())) {
                return false;
            }
            if(!interfaceName.equals(other.getInterfaceName())){
                return false;
            }
            if(!params.equals(other.getParams())) {
                return false;
            }
            if(!return_type.equals(other.getRetType())) {
                return false;
            }
            if(!param_names.equals(other.getParamNames())) {
                return false;
            }
            if(isSecure != other.isSecure) {
                return false;
            }

            Collections.sort(argList, new Comparator<ArgDef>() {
                public int compare(ArgDef one, ArgDef two){
                    return one.getArgName().compareTo(two.getArgName());
                }
            });
            Collections.sort(other.argList, new Comparator<ArgDef>() {
                public int compare(ArgDef one, ArgDef two){
                    return one.getArgName().compareTo(two.getArgName());
                }
            });
            if(!argList.equals(other.argList)) {
                return false;
            }

            return true;
        }
        return false;
    }
} // class SignalDef

/**
 * Data class representing a method or signal argument
 */
class ArgDef {
    public String argName;
    public String argType;
    public String argDirection;
    public String variantTypes;

    /**
     * Create new method argument
     * 
     * @param name
     *            the name of method argument
     * @param type
     *            the type of method argument
     * @param direct
     *            the direction of method argument
     */
    public ArgDef(String name, String type, String direct, String variants) {
        argName = name;
        argType = type;
        argDirection = direct;
        variantTypes = variants;
    }

    /**
     * Return the methods argument name
     * 
     * @return the methods argument name
     */
    public String getArgName() {
        return argName;
    }

    /**
     * Return the methods argument type
     * 
     * @return the methods argument type
     */
    public String getArgType() {
        return argType;
    }

    /**
     * Return the methods argument direction i.e. in or out
     * 
     * @return the methods argument direction
     */
    public String getArgDirection() {
        return argDirection;
    }
    
    /**
     *  Return the possible variant types of the argument.
     *  
     * @return
     */
    public String getVariantTypes(){
        return variantTypes;
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) {
            return false;
        }
        if(other == this) {
            return true;
        }
        if(this.getClass() != other.getClass()) {
            return false;
        }
        ArgDef otherArgDef = (ArgDef)other;
        if(!argName.equals(otherArgDef.getArgName())) {
            return false;
        }
        if(!argType.equals(otherArgDef.getArgType())) {
            return false;
        }
        if(argDirection != null && otherArgDef.getArgDirection() != null) {
            if(!argDirection.equals(otherArgDef.getArgDirection())) {
                return false;
            }
        }

        return true;
    }

} // class ArgDef
