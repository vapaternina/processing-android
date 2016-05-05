/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-16 The Processing Foundation

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.mode.android;

import processing.app.Base;
import processing.app.Messages;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AVD {
  static private final String AVD_CREATE_PRIMARY =
    "An error occurred while running “android create avd”";

  static private final String AVD_CREATE_SECONDARY =
    "The default Android emulator could not be set up. Make sure<br>" +
    "that the Android SDK is installed properly, and that the<br>" +
    "system images are installed for level %s.<br>" +
    "(Between you and me, occasionally, this error is a red herring,<br>" +
    "and your sketch may be launching shortly.)";

  static private final String AVD_LOAD_PRIMARY =
    "There is an error with the Processing AVD.";
  static private final String AVD_LOAD_SECONDARY =
    "This could mean that the Android tools need to be updated,<br>" +
    "or that the Processing AVD should be deleted (it will<br>" +
    "automatically re-created the next time you run Processing).<br>" +
    "Open the Android SDK Manager (underneath the Android menu)<br>" +
    "to check for any errors.";

  static private final String AVD_TARGET_PRIMARY =
    "The Google APIs are not installed properly";
  static private final String AVD_TARGET_SECONDARY =
    "Please re-read the installation instructions for Processing<br>" +
    "found at http://android.processing.org and try again.";

  static final String DEFAULT_SKIN = "WVGA800";
  static final String DEFAULT_SDCARD_SIZE = "64M";

  /** Name of this avd. */
  protected String name;

  /** "android-7" or "Google Inc.:Google APIs:7" */
  protected String target;

  static ArrayList<String> avdList;
  static ArrayList<String> badList;
//  static ArrayList<String> skinList;

  private Map<String, String> preferredAbi = new HashMap<>(30);
  private static List<String> abiList = new ArrayList<>();

  /** Default virtual device used by Processing. */
  static public final AVD defaultAVD =
    new AVD("Processing-0" + Base.getRevision(),
            "android-" + AndroidBuild.sdkVersion);
//            "Google Inc.:Google APIs:" + AndroidBuild.sdkVersion);

  public AVD(final String name, final String target) {
    this.name = name;
    this.target = target;

    initializeAbiList();
  }

  private void initializeAbiList() {
	  if (abiList.size() == 0) {
		  //The order in this list determines the preference of one abi over the other
		  abiList.add("armeabi");
		  abiList.add("x86");
		  abiList.add("x86_64");
	  }
  }


  static protected void list(final AndroidSDK sdk) throws IOException {
    try {
      avdList = new ArrayList<String>();
      badList = new ArrayList<String>();
      ProcessResult listResult =
        new ProcessHelper(sdk.getAndroidToolPath(), "list", "avds").execute();
      if (listResult.succeeded()) {
        boolean badness = false;
        for (String line : listResult) {
          String[] m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)");
          if (m != null) {
            if (!badness) {
//              System.out.println("good: " + m[1]);
              avdList.add(m[1]);
            } else {
//              System.out.println("bad: " + m[1]);
              badList.add(m[1]);
            }
//          } else {
//            System.out.println("nope: " + line);
          }
          // "The following Android Virtual Devices could not be loaded:"
          if (line.contains("could not be loaded:")) {
//            System.out.println("starting the bad list");
//            System.err.println("Could not list AVDs:");
//            System.err.println(listResult);
            badness = true;
//            break;
          }
        }
      } else {
        System.err.println("Unhappy inside exists()");
        System.err.println(listResult);
      }
    } catch (final InterruptedException ie) { }
  }


  protected boolean exists(final AndroidSDK sdk) throws IOException {
    if (avdList == null) {
      list(sdk);
    }
    for (String avd : avdList) {
      if (Base.DEBUG) {
        System.out.println("AVD.exists() checking for " + name + " against " + avd);
      }
      if (avd.equals(name)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Return true if a member of the renowned and prestigious
   * "The following Android Virtual Devices could not be loaded:" club.
   * (Prestigious may also not be the right word.)
   */
  protected boolean badness() {
    for (String avd : badList) {
      if (avd.equals(name)) {
        return true;
      }
    }
    return false;
  }


  protected boolean create(final AndroidSDK sdk) throws IOException {

	final String[] list_abi = {
	  sdk.getAndroidToolPath(),
	  "list", "targets"
	};

	ProcessHelper p = new ProcessHelper(list_abi);
	try {
	  final ProcessResult abiListResult = p.execute();
	  String api = null;
	  String abi = null;
	  for (String line : abiListResult) {
		String[] m = PApplet.match(line, "API\\slevel:\\s(\\S+)");
		if (m != null) {
		  api = m[1];
		}

		m = PApplet.match(line, "Tag\\/ABIs\\s:\\sdefault\\/(\\S+)");
		if (m != null) {
		  abi = m[1];

		  if (api != null && abi != null) {
			if (preferredAbi.get(api) == null) {
			  preferredAbi.put(api, abi);
			} else if (abiList.indexOf(preferredAbi.get(api)) < abiList.indexOf(abi)) {
			  preferredAbi.put(api, abi);
			}
			api = null;
			abi = null;
		  }
		}
	  }
	} catch (InterruptedException e) {}

	if (preferredAbi.get(AndroidBuild.sdkVersion) == null) {
	  return false;
	}

    final String[] params = {
      sdk.getAndroidToolPath(),
      "create", "avd",
      "-n", name,
      "-t", target,
      "-c", DEFAULT_SDCARD_SIZE,
      "-s", DEFAULT_SKIN,
      "--abi", preferredAbi.get(AndroidBuild.sdkVersion)
    };

    // Set the list to null so that exists() will check again
    avdList = null;

    p = new ProcessHelper(params);
    try {
      // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
//      System.out.println("CREATE AVD STARTING");
      final ProcessResult createAvdResult = p.execute("no");
//      System.out.println("CREATE AVD HAS COMPLETED");
      if (createAvdResult.succeeded()) {
        return true;
      }
      if (createAvdResult.toString().contains("Target id is not valid")) {
        // They didn't install the Google APIs
        Messages.showWarningTiered("Android Error", AVD_TARGET_PRIMARY, AVD_TARGET_SECONDARY, null);
//        throw new IOException("Missing required SDK components");
      } else {
        // Just generally not working
//        Base.showWarning("Android Error", AVD_CREATE_ERROR, null);
        Messages.showWarningTiered("Android Error", AVD_CREATE_PRIMARY, AVD_CREATE_SECONDARY, null);
        System.out.println(createAvdResult);
//        throw new IOException("Error creating the AVD");
      }
      //System.err.println(createAvdResult);
    } catch (final InterruptedException ie) { }

    return false;
  }


  static public boolean ensureProperAVD(final AndroidSDK sdk) {
    try {
      if (defaultAVD.exists(sdk)) {
//        System.out.println("the avd exists");
        return true;
      }
//      if (badList.contains(defaultAVD)) {
      if (defaultAVD.badness()) {
//        Base.showWarning("Android Error", AVD_CANNOT_LOAD, null);
        Messages.showWarningTiered("Android Error", AVD_LOAD_PRIMARY, AVD_LOAD_SECONDARY, null);
        return false;
      }
      if (defaultAVD.create(sdk)) {
//        System.out.println("the avd was created");
        return true;
      }
    } catch (final Exception e) {
//      Base.showWarning("Android Error", AVD_CREATE_ERROR, e);
      Messages.showWarningTiered("Android Error", AVD_CREATE_PRIMARY,
                                 String.format(AVD_CREATE_SECONDARY,
                                               AndroidBuild.sdkVersion), null);
    }
    return false;
  }
}
