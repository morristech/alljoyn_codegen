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
import java.util.StringTokenizer;

public class ObjectData {
    public InterfaceDescription inter;
    public String objPath;
    public String objName;
    public boolean useFullPath;

    public ObjectData() {
        inter = null;
        objName = null;
        objPath = "";
        useFullPath = false;
    }

    public boolean equals(Object obj) {
        if(obj instanceof ObjectData) {
            ObjectData other = (ObjectData)obj;
            if(objPath.equals(other.objPath)) {
                return true;
            }
            if(objName.equals(other.objName)) {
                ParseAJXML.useFullPath = true;
            }
        }

        return false;
    }

    public void updateObjectName() {
        objName = objPath.replaceAll("/", "_");
        if(objName.charAt(0) == '_') {
            objName = objName.substring(1);
        }
    }
}
