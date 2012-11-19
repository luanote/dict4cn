/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/*
 * This package is based on the work done by Timothy Gerard Endres
 * (time@ice.com) to whom the Ant project is very grateful for his great code.
 */

package org.apache.tools.tar;

import java.io.File;
import java.util.Date;
import java.util.Locale;

/**
 * This class represents an entry in a Tar archive. It consists of the entry's header, as well as the entry's File. Entries can be instantiated in one of three
 * ways, depending on how they are to be used.
 * <p>
 * TarEntries that are created from the header bytes read from an archive are instantiated with the TarEntry( byte[] ) constructor. These entries will be used
 * when extracting from or listing the contents of an archive. These entries have their header filled in using the header bytes. They also set the File to null,
 * since they reference an archive entry not a file.
 * <p>
 * TarEntries that are created from Files that are to be written into an archive are instantiated with the TarEntry( File ) constructor. These entries have
 * their header filled in using the File's information. They also keep a reference to the File for convenience when writing entries.
 * <p>
 * Finally, TarEntries can be constructed from nothing but a name. This allows the programmer to construct the entry by hand, for instance when only an
 * InputStream is available for writing to the archive, and the header information is constructed from other information. In this case the header fields are set
 * to defaults and the File is set to null.
 * 
 * <p>
 * The C structure for a Tar Entry's header is:
 * 
 * <pre>
 * struct header {
 * char name[NAMSIZ];
 * char mode[8];
 * char uid[8];
 * char gid[8];
 * char size[12];
 * char mtime[12];
 * char chksum[8];
 * char linkflag;
 * char linkname[NAMSIZ];
 * char magic[8];
 * char uname[TUNMLEN];
 * char gname[TGNMLEN];
 * char devmajor[8];
 * char devminor[8];
 * } header;
 * </pre>
 * 
 */

public class TarEntry implements TarConstants {
  /** The entry's name. */
  private StringBuffer    name;

  /** The entry's permission mode. */
  private int             mode;

  /** The entry's user id. */
  private int             userId;

  /** The entry's group id. */
  private int             groupId;

  /** The entry's size. */
  private long            size;

  /** The entry's modification time. */
  private long            modTime;

  /** The entry's link flag. */
  private byte            linkFlag;

  /** The entry's link name. */
  private StringBuffer    linkName;

  /** The entry's magic tag. */
  private StringBuffer    magic;

  /** The entry's user name. */
  private StringBuffer    userName;

  /** The entry's group name. */
  private StringBuffer    groupName;

  /** The entry's major device number. */
  private int             devMajor;

  /** The entry's minor device number. */
  private int             devMinor;

  /** The entry's file reference */
  private File            file;

  /** Maximum length of a user's name in the tar file */
  public static final int MAX_NAMELEN       = 31;

  /** Default permissions bits for directories */
  public static final int DEFAULT_DIR_MODE  = 040755;

  /** Default permissions bits for files */
  public static final int DEFAULT_FILE_MODE = 0100644;

  /** Convert millis to seconds */
  public static final int MILLIS_PER_SECOND = 1000;

  /**
   * Construct an empty entry and prepares the header values.
   */
  private TarEntry() {
    this.magic = new StringBuffer(TarConstants.TMAGIC);
    this.name = new StringBuffer();
    this.linkName = new StringBuffer();

    String user = System.getProperty("user.name", "");

    if (user.length() > TarEntry.MAX_NAMELEN) {
      user = user.substring(0, TarEntry.MAX_NAMELEN);
    }

    this.userId = 0;
    this.groupId = 0;
    this.userName = new StringBuffer(user);
    this.groupName = new StringBuffer("");
    this.file = null;
  }

  /**
   * Construct an entry with only a name. This allows the programmer to construct the entry's header "by hand". File is set to null.
   * 
   * @param name
   *          the entry name
   */
  public TarEntry(final String name) {
    this(name, false);
  }

  /**
   * Construct an entry with only a name. This allows the programmer to construct the entry's header "by hand". File is set to null.
   * 
   * @param name
   *          the entry name
   * @param preserveLeadingSlashes
   *          whether to allow leading slashes in the name.
   */
  public TarEntry(String name, final boolean preserveLeadingSlashes) {
    this();

    String n = TarEntry.normalizeFileName(name, preserveLeadingSlashes);
    final boolean isDir = n.endsWith("/");

    this.devMajor = 0;
    this.devMinor = 0;
    this.name = new StringBuffer(n);
    this.mode = isDir ? TarEntry.DEFAULT_DIR_MODE : TarEntry.DEFAULT_FILE_MODE;
    this.linkFlag = isDir ? TarConstants.LF_DIR : TarConstants.LF_NORMAL;
    this.userId = 0;
    this.groupId = 0;
    this.size = 0;
    this.modTime = (new Date()).getTime() / TarEntry.MILLIS_PER_SECOND;
    this.linkName = new StringBuffer("");
    this.userName = new StringBuffer("");
    this.groupName = new StringBuffer("");
    this.devMajor = 0;
    this.devMinor = 0;

  }

  /**
   * Construct an entry with a name and a link flag.
   * 
   * @param name
   *          the entry name
   * @param linkFlag
   *          the entry link flag.
   */
  public TarEntry(final String name, final byte linkFlag) {
    this(name);
    this.linkFlag = linkFlag;
    if (linkFlag == TarConstants.LF_GNUTYPE_LONGNAME) {
      this.magic = new StringBuffer(TarConstants.GNU_TMAGIC);
    }
  }

  /**
   * Construct an entry for a file. File is set to file, and the header is constructed from information from the file.
   * 
   * @param file
   *          The file that the entry represents.
   */
  public TarEntry(final File file) {
    this();

    this.file = file;

    final String fileName = TarEntry.normalizeFileName(file.getPath(), false);
    this.linkName = new StringBuffer("");
    this.name = new StringBuffer(fileName);

    if (file.isDirectory()) {
      this.mode = TarEntry.DEFAULT_DIR_MODE;
      this.linkFlag = TarConstants.LF_DIR;

      final int nameLength = this.name.length();
      if ((nameLength == 0) || (this.name.charAt(nameLength - 1) != '/')) {
        this.name.append("/");
      }
      this.size = 0;
    } else {
      this.mode = TarEntry.DEFAULT_FILE_MODE;
      this.linkFlag = TarConstants.LF_NORMAL;
      this.size = file.length();
    }

    this.modTime = file.lastModified() / TarEntry.MILLIS_PER_SECOND;
    this.devMajor = 0;
    this.devMinor = 0;
  }

  /**
   * Construct an entry from an archive's header bytes. File is set to null.
   * 
   * @param headerBuf
   *          The header bytes from a tar archive entry.
   */
  public TarEntry(final byte[] headerBuf) {
    this();
    this.parseTarHeader(headerBuf);
  }

  /**
   * Determine if the two entries are equal. Equality is determined by the header names being equal.
   * 
   * @param it
   *          Entry to be checked for equality.
   * @return True if the entries are equal.
   */
  public boolean equals(final TarEntry it) {
    return this.getName().equals(it.getName());
  }

  /**
   * Determine if the two entries are equal. Equality is determined by the header names being equal.
   * 
   * @param it
   *          Entry to be checked for equality.
   * @return True if the entries are equal.
   */
  @Override
  public boolean equals(final Object it) {
    if ((it == null) || (this.getClass() != it.getClass())) {
      return false;
    }
    return this.equals((TarEntry) it);
  }

  /**
   * Hashcodes are based on entry names.
   * 
   * @return the entry hashcode
   */
  @Override
  public int hashCode() {
    return this.getName().hashCode();
  }

  /**
   * Determine if the given entry is a descendant of this entry. Descendancy is determined by the name of the descendant starting with this entry's name.
   * 
   * @param desc
   *          Entry to be checked as a descendent of this.
   * @return True if entry is a descendant of this.
   */
  public boolean isDescendent(final TarEntry desc) {
    return desc.getName().startsWith(this.getName());
  }

  /**
   * Get this entry's name.
   * 
   * @return This entry's name.
   */
  public String getName() {
    return this.name.toString();
  }

  /**
   * Set this entry's name.
   * 
   * @param name
   *          This entry's new name.
   */
  public void setName(final String name) {
    this.name = new StringBuffer(TarEntry.normalizeFileName(name, false));
  }

  /**
   * Set the mode for this entry
   * 
   * @param mode
   *          the mode for this entry
   */
  public void setMode(final int mode) {
    this.mode = mode;
  }

  /**
   * Get this entry's link name.
   * 
   * @return This entry's link name.
   */
  public String getLinkName() {
    return this.linkName.toString();
  }

  /**
   * Get this entry's user id.
   * 
   * @return This entry's user id.
   */
  public int getUserId() {
    return this.userId;
  }

  /**
   * Set this entry's user id.
   * 
   * @param userId
   *          This entry's new user id.
   */
  public void setUserId(final int userId) {
    this.userId = userId;
  }

  /**
   * Get this entry's group id.
   * 
   * @return This entry's group id.
   */
  public int getGroupId() {
    return this.groupId;
  }

  /**
   * Set this entry's group id.
   * 
   * @param groupId
   *          This entry's new group id.
   */
  public void setGroupId(final int groupId) {
    this.groupId = groupId;
  }

  /**
   * Get this entry's user name.
   * 
   * @return This entry's user name.
   */
  public String getUserName() {
    return this.userName.toString();
  }

  /**
   * Set this entry's user name.
   * 
   * @param userName
   *          This entry's new user name.
   */
  public void setUserName(final String userName) {
    this.userName = new StringBuffer(userName);
  }

  /**
   * Get this entry's group name.
   * 
   * @return This entry's group name.
   */
  public String getGroupName() {
    return this.groupName.toString();
  }

  /**
   * Set this entry's group name.
   * 
   * @param groupName
   *          This entry's new group name.
   */
  public void setGroupName(final String groupName) {
    this.groupName = new StringBuffer(groupName);
  }

  /**
   * Convenience method to set this entry's group and user ids.
   * 
   * @param userId
   *          This entry's new user id.
   * @param groupId
   *          This entry's new group id.
   */
  public void setIds(final int userId, final int groupId) {
    this.setUserId(userId);
    this.setGroupId(groupId);
  }

  /**
   * Convenience method to set this entry's group and user names.
   * 
   * @param userName
   *          This entry's new user name.
   * @param groupName
   *          This entry's new group name.
   */
  public void setNames(final String userName, final String groupName) {
    this.setUserName(userName);
    this.setGroupName(groupName);
  }

  /**
   * Set this entry's modification time. The parameter passed to this method is in "Java time".
   * 
   * @param time
   *          This entry's new modification time.
   */
  public void setModTime(final long time) {
    this.modTime = time / TarEntry.MILLIS_PER_SECOND;
  }

  /**
   * Set this entry's modification time.
   * 
   * @param time
   *          This entry's new modification time.
   */
  public void setModTime(final Date time) {
    this.modTime = time.getTime() / TarEntry.MILLIS_PER_SECOND;
  }

  /**
   * Set this entry's modification time.
   * 
   * @return time This entry's new modification time.
   */
  public Date getModTime() {
    return new Date(this.modTime * TarEntry.MILLIS_PER_SECOND);
  }

  /**
   * Get this entry's file.
   * 
   * @return This entry's file.
   */
  public File getFile() {
    return this.file;
  }

  /**
   * Get this entry's mode.
   * 
   * @return This entry's mode.
   */
  public int getMode() {
    return this.mode;
  }

  /**
   * Get this entry's file size.
   * 
   * @return This entry's file size.
   */
  public long getSize() {
    return this.size;
  }

  /**
   * Set this entry's file size.
   * 
   * @param size
   *          This entry's new file size.
   */
  public void setSize(final long size) {
    this.size = size;
  }

  /**
   * Indicate if this entry is a GNU long name block
   * 
   * @return true if this is a long name extension provided by GNU tar
   */
  public boolean isGNULongNameEntry() {
    return (this.linkFlag == TarConstants.LF_GNUTYPE_LONGNAME) && this.name.toString().equals(TarConstants.GNU_LONGLINK);
  }

  /**
   * Return whether or not this entry represents a directory.
   * 
   * @return True if this entry is a directory.
   */
  public boolean isDirectory() {
    if (this.file != null) {
      return this.file.isDirectory();
    }

    if (this.linkFlag == TarConstants.LF_DIR) {
      return true;
    }

    if (this.getName().endsWith("/")) {
      return true;
    }

    return false;
  }

  /**
   * If this entry represents a file, and the file is a directory, return an array of TarEntries for this entry's children.
   * 
   * @return An array of TarEntry's for this entry's children.
   */
  public TarEntry[] getDirectoryEntries() {
    if ((this.file == null) || !this.file.isDirectory()) {
      return new TarEntry[0];
    }

    final String[] list = this.file.list();
    final TarEntry[] result = new TarEntry[list.length];

    for (int i = 0; i < list.length; ++i) {
      result[i] = new TarEntry(new File(this.file, list[i]));
    }

    return result;
  }

  /**
   * Write an entry's header information to a header buffer.
   * 
   * @param outbuf
   *          The tar entry header buffer to fill in.
   */
  public void writeEntryHeader(final byte[] outbuf) {
    int offset = 0;

    offset = TarUtils.getNameBytes(this.name, outbuf, offset, TarConstants.NAMELEN);
    offset = TarUtils.getOctalBytes(this.mode, outbuf, offset, TarConstants.MODELEN);
    offset = TarUtils.getOctalBytes(this.userId, outbuf, offset, TarConstants.UIDLEN);
    offset = TarUtils.getOctalBytes(this.groupId, outbuf, offset, TarConstants.GIDLEN);
    offset = TarUtils.getLongOctalBytes(this.size, outbuf, offset, TarConstants.SIZELEN);
    offset = TarUtils.getLongOctalBytes(this.modTime, outbuf, offset, TarConstants.MODTIMELEN);

    final int csOffset = offset;

    for (int c = 0; c < TarConstants.CHKSUMLEN; ++c) {
      outbuf[offset++] = (byte) ' ';
    }

    outbuf[offset++] = this.linkFlag;
    offset = TarUtils.getNameBytes(this.linkName, outbuf, offset, TarConstants.NAMELEN);
    offset = TarUtils.getNameBytes(this.magic, outbuf, offset, TarConstants.MAGICLEN);
    offset = TarUtils.getNameBytes(this.userName, outbuf, offset, TarConstants.UNAMELEN);
    offset = TarUtils.getNameBytes(this.groupName, outbuf, offset, TarConstants.GNAMELEN);
    offset = TarUtils.getOctalBytes(this.devMajor, outbuf, offset, TarConstants.DEVLEN);
    offset = TarUtils.getOctalBytes(this.devMinor, outbuf, offset, TarConstants.DEVLEN);

    while (offset < outbuf.length) {
      outbuf[offset++] = 0;
    }

    final long chk = TarUtils.computeCheckSum(outbuf);

    TarUtils.getCheckSumOctalBytes(chk, outbuf, csOffset, TarConstants.CHKSUMLEN);
  }

  /**
   * Parse an entry's header information from a header buffer.
   * 
   * @param header
   *          The tar entry header buffer to get information from.
   */
  public void parseTarHeader(final byte[] header) {
    int offset = 0;

    this.name = TarUtils.parseName(header, offset, TarConstants.NAMELEN);
    offset += TarConstants.NAMELEN;
    this.mode = (int) TarUtils.parseOctal(header, offset, TarConstants.MODELEN);
    offset += TarConstants.MODELEN;
    this.userId = (int) TarUtils.parseOctal(header, offset, TarConstants.UIDLEN);
    offset += TarConstants.UIDLEN;
    this.groupId = (int) TarUtils.parseOctal(header, offset, TarConstants.GIDLEN);
    offset += TarConstants.GIDLEN;
    this.size = TarUtils.parseOctal(header, offset, TarConstants.SIZELEN);
    offset += TarConstants.SIZELEN;
    this.modTime = TarUtils.parseOctal(header, offset, TarConstants.MODTIMELEN);
    offset += TarConstants.MODTIMELEN;
    offset += TarConstants.CHKSUMLEN;
    this.linkFlag = header[offset++];
    this.linkName = TarUtils.parseName(header, offset, TarConstants.NAMELEN);
    offset += TarConstants.NAMELEN;
    this.magic = TarUtils.parseName(header, offset, TarConstants.MAGICLEN);
    offset += TarConstants.MAGICLEN;
    this.userName = TarUtils.parseName(header, offset, TarConstants.UNAMELEN);
    offset += TarConstants.UNAMELEN;
    this.groupName = TarUtils.parseName(header, offset, TarConstants.GNAMELEN);
    offset += TarConstants.GNAMELEN;
    this.devMajor = (int) TarUtils.parseOctal(header, offset, TarConstants.DEVLEN);
    offset += TarConstants.DEVLEN;
    this.devMinor = (int) TarUtils.parseOctal(header, offset, TarConstants.DEVLEN);
  }

  /**
   * Strips Windows' drive letter as well as any leading slashes, turns path separators into forward slahes.
   */
  private static String normalizeFileName(String fileName, final boolean preserveLeadingSlashes) {
    final String osname = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    String f = fileName;
    if (osname != null) {

      // Strip off drive letters!
      // REVIEW Would a better check be "(File.separator == '\')"?

      if (osname.startsWith("windows")) {
        if (f.length() > 2) {
          final char ch1 = f.charAt(0);
          final char ch2 = f.charAt(1);

          if ((ch2 == ':') && (((ch1 >= 'a') && (ch1 <= 'z')) || ((ch1 >= 'A') && (ch1 <= 'Z')))) {
            f = f.substring(2);
          }
        }
      } else if (osname.indexOf("netware") > -1) {
        final int colon = f.indexOf(':');
        if (colon != -1) {
          f = f.substring(colon + 1);
        }
      }
    }

    f = f.replace(File.separatorChar, '/');

    // No absolute pathnames
    // Windows (and Posix?) paths can start with "\\NetworkDrive\",
    // so we loop on starting /'s.
    while (!preserveLeadingSlashes && f.startsWith("/")) {
      f = f.substring(1);
    }
    return f;
  }
}
