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

/**
 * The CodeGenPrint function has a list of helper functions that can be used to 
 * make generating nice looking C++ file output simpler.
 */
public class FormatCode {
    static int indentSize = 4;
    //tabSize is the same as indent size if the indentChar is a space
    static int tabSize = indentSize; 
    static char indentChar = ' ';
	
    /**
     * used to generate an indented line 
     * @param count the number of indents the line will use
     * @return a string representing the indented line
     */
    public static String indent(int count){
        String output = "";
        for(int i=0; i < count; i++){
            for(int j=0; j < indentSize; j++){
                output += indentChar;
            }
        }
        return output;
    }
	
	
    /**
     * Given a single line of code indent that line the specified depth
     * @param code - the line of code that needs indenting
     * @param indentDepth - the number of indents the line will have
     * @return return an indented line.
     */
    public static String indent(String code, int indentDepth){
        String output = "";
        output += indent(indentDepth);
        output += code;
        return output;
    }
	
    /**
     * Given a single line of code indent that line the specified depth and add
     * a new line to the end of the code.
     * @param code - the line of code that needs indenting
     * @param indentDepth - the number of indents the line will have
     * @return return an indented line.
     */
    public static String indentln(String code, int indentDepth){
        return indent(code, indentDepth) + "\n";
    }
	
    public static String blockComment(String input){
        return blockComment(input, 0, 80);
    }

    public static String blockComment(String input, int indentDepth){
        return blockComment(input, indentDepth, 80);
    }
	
    /**
     * Take a string of unknown length and return a C++ comment block that is
     * less than the specified character column. 
     * A BlockComment is a comment that outlines the top and bottom of the 
     * comment to make it stand out more from the rest of the code.  This is 
     * typically used at the tops of methods, classes, and functions.
     * 
     * @param input the comment that belongs in the block comment
     * @param indentDepth how far this comment will be indented from the left 
     *                    edge of the screen.
     * @param column - the column width of the comment in number of characters
     * @return the formated sting in a block comment
     */
    public static String blockComment(String input, int indentDepth, int column){
        String output="";
        output += indent(indentDepth) ;
        output += "/*";
        for (int i=2; i < (column - tabSize * indentDepth); i++){
			
            output +="-";
        }
        output += "\n";
		
        output += starPrefixedStrings(input, indentDepth,column);
		
        output += indent(indentDepth) + " *";
        for (int i=3; i < (column - tabSize * indentDepth); i++){
            output +="-";
        } 
        output += "*/\n";
        return output;
    }
	
    public static String comment(String input){
        return comment(input, 0, 80);
    }
    public static String comment(String input, int indentDepth){
        return comment(input, indentDepth, 80);
    }
	
    /**
     * 
     * Take a string of unknown length and return a C++ comment that is
     * less than the specified character column. 
     * 
     * @param input the comment that belongs in the comment
     * @param indentDepth how far this comment will be indented from the left 
     *                    edge of the screen.
     * @param column - the column width of the comment in number of characters
     * @return the formated sting in a block comment
     */
    public static String comment(String input, int indentDepth, int column){
        String output = "";
        int adjustedColumn = column - 6 - (tabSize * indentDepth);
        if(input.length() > adjustedColumn || input.indexOf("\n") > -1){
            output += indent("/*\n", indentDepth);
            output += starPrefixedStrings(input, indentDepth,column);
            output += indent(" */\n", indentDepth);
        } else {
            output += indent("/* " + input + " */\n", indentDepth);
        }
        return output;            
    }
    
    /**
     * Take a string of unknown length and return a string that no line is 
     * longer than the specified character column and each line is prefixed by
     * a '*' character that is common in C++ multi-line comments. 
     * 
     * @param input the comment that belongs in the comment
     * @param indentDepth how far this comment will be indented from the left 
     *                    edge of the screen.
     * @param column - the column width of the comment in number of characters
     * @return the formated sting in a block comment
     */
    private static String starPrefixedStrings(String input, int indentDepth, int column){
        String output ="";
        // Column length adjusted for white space and the "*" characters
        int adjustedColumn = column - 3 - (tabSize * indentDepth);
        int startindex =0;
        int endindex = adjustedColumn;
        int stringPointer;

        //only process the input if it is larger then the specified width
        while ((input.substring(startindex)).length() > adjustedColumn || 
               input.indexOf("\n", startindex) > -1 ) {
            
            output += indent(indentDepth) + " * "; //add star to block comment
            //check to see if a new line is within the string width
            stringPointer = input.indexOf("\n", startindex)+1;
            if((stringPointer - startindex) <= adjustedColumn && 
               (stringPointer - startindex) > 0){
                output += input.substring(startindex, stringPointer);
                startindex = stringPointer;
                endindex = stringPointer + adjustedColumn;
                continue;
            }
            /*
             * find the character after the space closest to the end of the
             * string width
             */ 
            stringPointer = input.lastIndexOf(" ", endindex) + 1;
            // Check to see it the string is greater then <width>
            if (stringPointer <= startindex) {
                //find character after the first found space.
                stringPointer = input.indexOf(" ", endindex) + 1;
                if (stringPointer == 0){ 
                    //this code should never run
                    output += input.substring(startindex) + "\n";
                    continue;
                } else {
                    output += input.substring(startindex, stringPointer) + "\n";
                    startindex = stringPointer;
                    endindex = stringPointer + adjustedColumn;
                }
                
            } else {
                output += input.substring(startindex, stringPointer) + "\n";
                startindex = stringPointer;
                endindex = stringPointer + adjustedColumn;
            }
        }
        
        if ((input.substring(startindex)).length() > 0) {
            output += indent(indentDepth) + " * "; //add star to block comment
            output += input.substring(startindex) + "\n";
        }
        return output;
    }
    
}
