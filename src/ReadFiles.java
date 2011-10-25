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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;

/**
 * AjnReadFiles class opens and reads the files, and returns their contents as
 * strings.  It can read either from the files system or from the JAR file in
 * which the project is contained.
 */
public class ReadFiles {

    /**
     * Opens a file on the file system and returns the content in a string.
     * @param file The file to be read.
     * @return content of the file in a String.
     */
    public static String getStringFromFileSys(String file) {
        BufferedReader reader;
        String xml = "";
        String tempStr;
        try {
            reader = new BufferedReader(new FileReader(file));
            while (true) {
                if ((tempStr = reader.readLine()) != null) {
                    xml += tempStr + "\n";
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println(e.toString());
            return null;
        }
        return xml;

    } /* getStringFromFileSys() */

    /**
     * Opens a file that is contained in the executable JAR of the application
     * and returns the contents as a string.
     * @param file: name of the file to open
     * @return the contents of the file as a String.
     */
    public static String getStringFromJAR(String file) {
        final int bufSize = 1024;
        int len;
        String contents = "";
        InputStream s;
        byte[] buffer = new byte[bufSize];

        // read the entire contents of the file into 'contents' 
        try {
            s = Class.forName("ReadFiles").getResourceAsStream(file);
            do {
                len = s.read(buffer, 0, bufSize);
                contents += new String(buffer, 0, len);
            } while( len == bufSize );
            s.close();

        } catch(java.lang.Exception exp) {
            System.out.println("Failed to read the '" + file + "' file");
            System.out.println(exp);
            System.exit(1);
        }

        return contents;

    } /* getStringFromFileInJAR() */

} /* Class: AjnReadFiles */
