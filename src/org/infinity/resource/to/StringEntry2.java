// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.to;

import org.infinity.datatype.TextEdit;
import org.infinity.resource.AbstractStruct;

public class StringEntry2 extends AbstractStruct
{
  // TOH/StringEntry2-specific field labels
  public static final String TOH_STRING       = "String entry";
  public static final String TOH_STRING_TEXT  = "Override string";

  public StringEntry2() throws Exception
  {
    super(null, TOH_STRING, new byte[524], 0);
  }

  public StringEntry2(AbstractStruct superStruct, byte buffer[], int offset, int nr) throws Exception
  {
    super(superStruct, TOH_STRING + " " + nr, buffer, offset);
  }

  public StringEntry2(AbstractStruct superStruct, String name, byte[] buffer, int offset) throws Exception
  {
    super(superStruct, name, buffer, offset);
  }

  @Override
  public int read(byte[] buffer, int offset) throws Exception
  {
    int len = 0;
    while ((len < buffer.length - offset) && buffer[offset + len] != 0) {
      len++;
    }
    TextEdit edit = new TextEdit(buffer, offset, len + 1, TOH_STRING_TEXT);
    edit.setEolType(TextEdit.EOLType.UNIX);
    edit.setCharset("UTF-8");
    edit.setEditable(false);
    edit.setStringTerminated(true);
    addField(edit);
    return offset + len + 1;
  }
}