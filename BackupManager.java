import java.awt.Color;
import java.awt.Point;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.MouseInfo;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.LinkOption;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Scanner;

import javax.swing.JFileChooser;

public class BackupManager implements Runnable
{
	
	// General tracking, etx
	static String knownDrives="";
	static boolean firstRun = true;
	static GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
	static DisplayPanel[] panels = new DisplayPanel[devices.length];
	static File inCopy;
	static Cursor normalCursor, transparentCursor;
	static int lmx, lmy;
	static long mouseActiveTime;
	
	// Device setup variables
	static boolean drawDeviceSetupWindow = false;
	static int deviceSetupStatus = 0;
	static File deviceSetupDrive;
	static String deviceSetupFeedback, deviceSetupInput = "";
	static File configFile;
	static PrintWriter out;
	
	// Non-static
	String driveName;
	File testDrive;
	Device device;
	
	public BackupManager(File testDrive, String driveName)
	{
		this.testDrive = testDrive;
		this.driveName = driveName;
	}
	public static void main(String[] args)
	{
		createWindows();
		setupCursors();
		while (true)
		{
			for (char driveChar = 'A'; driveChar <= 'Z'; driveChar++)
			{
				File drive = new File(driveChar + ":\\");
				String driveName = Character.toString(driveChar);
				if (drive.exists() && !isKnown(driveName))
				{
					if (!firstRun)
						new Thread(new BackupManager(drive, driveName)).start();
					markKnown(driveName);
				}
				else if (isKnown(driveName) && !drive.exists())
				{
					DisplayPanel.handleDeviceRemoval(driveName);
					makeUnknown(driveName);
				}
			}
			firstRun = false;
			handleCursorAutoHide();
		}
	}
	public void run()
	{
		DisplayPanel.consoleText = "";
		DisplayPanel.println(driveName + " mounted.");
		device = DisplayPanel.showRemovableDevice(driveName);
		DisplayPanel.setRemovableDeviceStatus(driveName, SyncStatus.SYNC_IN_PROGRESS);
		try
		{
			File configFile = new File(testDrive.getAbsolutePath() + "\\.~config");
			boolean driveHasConfig = configFile.exists();
			if (!driveHasConfig)
				promptSetupNewDevice(testDrive, configFile);
			loadConfig(configFile, testDrive);
			testDrive = device.repoBase;
			recursiveGitFind(testDrive);
		}
		catch(Exception ex)
		{
			DisplayPanel.setRemovableDeviceStatus(driveName, SyncStatus.HAD_FATAL_ERROR);
		}
		DisplayPanel.setRemovableDeviceStatus(driveName, SyncStatus.READY_TO_REMOVE);
	}
	public static void createWindows()
	{
		for (int i = 0; i < devices.length; i++)
			panels[i] = new DisplayPanel(devices[i], i);
	}
	public static void setupCursors()
	{
		normalCursor = panels[0].frame.getCursor();
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		transparentCursor = toolkit.createCustomCursor(new BufferedImage(1, 1, 3), new Point(0, 0), "blank");
	}
	public static void handleCursorAutoHide()
	{
		int mx = MouseInfo.getPointerInfo().getLocation().x;
		int my = MouseInfo.getPointerInfo().getLocation().y;
		if (my != lmy || mx != lmx)
		{
			mouseActiveTime = System.nanoTime();
			if (panels[0].frame.getCursor()==transparentCursor)
			{
				for (DisplayPanel panel : panels)
					panel.frame.setCursor(normalCursor);
			}
			lmx = mx;
			lmy = my;
		}
		else if (panels[0].frame.getCursor()==normalCursor && System.nanoTime()-mouseActiveTime > 500000000)
			for (DisplayPanel panel : panels)
				panel.frame.setCursor(transparentCursor);
	}
	public void promptSetupNewDevice(File testDrive, File configFile)
	{
		while (deviceSetupStatus != 0){try{Thread.sleep(100);}catch(InterruptedException ex){}}	// do not show multiple prompts at once!
		deviceSetupStatus = 1;
		drawDeviceSetupWindow = true;
		deviceSetupDrive = testDrive;
		deviceSetupFeedback = "This proccess will create a hidden file on your thumbdrive to track its status.\nPress enter to continue...\n";
		while (deviceSetupStatus != 0 && testDrive.exists()){try{Thread.sleep(100);}catch(InterruptedException ex){}}
		if (deviceSetupStatus != 0)
			deviceSetupStatus = 0;
		drawDeviceSetupWindow = false;
	}
	public void loadConfig(File configFile, File driveFile)
	{
		try
		{
			Scanner in = new Scanner(new FileInputStream(configFile));
			device.dname = in.nextLine();
			String path = in.nextLine();
			if (!path.equals("[device]"))
				device.repoBase = new File(in.nextLine());
			else
				device.repoBase = driveFile;
		}
		catch (IOException ex)
		{
			DisplayPanel.println("Could not load config file.");
			device.repoBase = driveFile;
		}
	}
	public static void markKnown(String fileName)
	{
		knownDrives += fileName + ",";
	}
	public static void makeUnknown(String fileName)
	{
		knownDrives = knownDrives.replaceAll(fileName + ",", "");
	}
	public static boolean isKnown(String fileName)
	{
		return knownDrives.contains(fileName);
	}
	public void recursiveGitFind(File dir)
	{
		if (dir.listFiles()==null)
		{
			DisplayPanel.println("Skip unreadable directory " + dir.getAbsolutePath());
			return;
		}
		for (File f : dir.listFiles())
		{
			if (f.getName().equals(".git"))
			{
				DisplayPanel.println(dir + " is a git repository...");
				String directoryName = dir.getName();
				if (!hasLocalCopy(directoryName))
				{
					DisplayPanel.println("\tBacking up previously unknown repository " + directoryName);
					backupRepository(dir);
					AnimationStatus.createAnimation(Color.GREEN, directoryName, "Backup Created");
					AnimationStatus.createAnimation(Color.GREEN, "?"+device.name);
					DisplayPanel.println("\t\tDone.");
				}
				else
				{
					boolean lock = isRepositoryLocked(directoryName);
					boolean force = isRepositoryForceablyUpdatingRemote(directoryName);
					if (lock)
					{
						AnimationStatus.createAnimation(Color.LIGHT_GRAY, directoryName, "Repository Locked");
						//AnimationStatus.createAnimation(Color.LIGHT_GRAY, "?"+device.name);
						DisplayPanel.println("\t[repository is locked]");
						break;
					}
					CommitInfo[] localInfo = null;
					CommitInfo[] remoteInfo = null;
					try
					{
						localInfo = getCommitInfo(new File("data/" + directoryName));
						remoteInfo = getCommitInfo(dir);
					}
					catch (Exception ex)
					{
						DisplayPanel.println("\tFailed to grab commit info.");
						AnimationStatus.createAnimation(Color.RED, directoryName, "Failed to grab commit info.");
						AnimationStatus.createAnimation(Color.RED, "?"+device.name);
					}
					if (localInfo[0].commitHash.equals(remoteInfo[0].commitHash))
					{
						DisplayPanel.println("\tRepositories are already up-to-date.");
						AnimationStatus.createAnimation(Color.GREEN, directoryName, "Already Up-To-Date");
						AnimationStatus.createAnimation(Color.GREEN, "?"+device.name);
					}
					else if (force)
					{
						DisplayPanel.println("\tForceably overwriting remote...");
						updateRemoteRepository(dir);
						DisplayPanel.println("\t\tDone.");
						AnimationStatus.createAnimation(Color.YELLOW, directoryName, "Written to Drive");
						AnimationStatus.createAnimation(Color.YELLOW, "?"+device.name);
					}
					else
					{
						DisplayPanel.println("\tPrepare to back up modified repository " + directoryName + "...");
						DisplayPanel.println("\tCheck compatability with local (looking for a commit path from local to remote...)");
						String localLatestHash = localInfo[0].commitHash;
						boolean foundMatch = false;
						int x;
						for (x = 0; x < remoteInfo.length; x++)
						{
							if (remoteInfo[x]==null||remoteInfo[x].commitHash==null)
								break;
							if (remoteInfo[x].commitHash.equals(localLatestHash))
							{
								foundMatch = true;
								break;
							}
						}
						if (!foundMatch)
						{							
							String remoteLatestHash = remoteInfo[0].commitHash;
							boolean foundBackwardsMatch = false;
							for (x = 0; x < localInfo.length; x++)
							{
								if (localInfo[x]==null||localInfo[x].commitHash==null)
									break;
								if (localInfo[x].commitHash.equals(remoteLatestHash))
								{
									foundBackwardsMatch = true;
									break;
								}
							}
							if (foundBackwardsMatch)
							{
								AnimationStatus.createAnimation(Color.RED, directoryName, "Out of Date Remote (-" + x + " commits)");
								AnimationStatus.createAnimation(Color.RED, "?"+device.name);
								DisplayPanel.println("\t\tRemote is " + x + (x==1?" commit":" commits") + " out of date.");
							}
							else
							{
								AnimationStatus.createAnimation(Color.RED, directoryName, "Different Branch");
								AnimationStatus.createAnimation(Color.RED, "?"+device.name);
								DisplayPanel.println("\t\tBranches do not match!");
							}
							return;
						}
						DisplayPanel.println("\t\tLocal is " + x + (x==1?" commit":" commits") + " out of date.");
						DisplayPanel.println("\tBacking up modified repository " + directoryName + "...");
						backupRepository(dir);
						DisplayPanel.println("\t\tDone.");
						AnimationStatus.createAnimation(Color.BLUE, directoryName, "Updated (+" + x + " commits)");
						AnimationStatus.createAnimation(Color.BLUE, "?"+device.name);
					}
				}
			}
			else if (f.isDirectory())
			{
				recursiveGitFind(f);
			}
		}
	}
	public static boolean hasLocalCopy(String repoName)
	{
		File localBase = new File("data/" + repoName);
		return localBase.exists();
	}
	public static boolean isRepositoryLocked(String repoName)
	{
		return new File("data/"+repoName+".~lock").exists();
	}
	public static boolean isRepositoryForceablyUpdatingRemote(String repoName)
	{
		return new File("data/"+repoName+".~force").exists();
	}
	public static void advanceDeviceSetupState()
	{
		deviceSetupStatus++;
		switch (deviceSetupStatus)
		{
			case 2:				
				configFile = new File(deviceSetupDrive.getAbsolutePath() + "/.~config");
				try{out = new PrintWriter(new FileOutputStream(configFile));}catch(IOException ex){ex.printStackTrace();}
				deviceSetupFeedback += "\nDrive name?\n";
				break;
			case 3:
				out.println(deviceSetupInput);
				deviceSetupFeedback += deviceSetupInput + "\n";
				deviceSetupInput = "";
				deviceSetupFeedback += "\n";
				deviceSetupFeedback += "Repository folder? (leave blank to default to the whole drive)\n";
				JFileChooser fileChooser = new JFileChooser(deviceSetupDrive);
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				fileChooser.showOpenDialog(null);
				File newBase = fileChooser.getSelectedFile();
				if (newBase == null || !newBase.exists() || !newBase.isDirectory() || newBase.getAbsolutePath().equals(deviceSetupDrive.getAbsolutePath()))
					out.println("[device]");
				else
					out.println(newBase.getAbsolutePath());
				deviceSetupStatus++;
			default:
				try
				{
					out.close();
					Path cpath = Paths.get(configFile.getAbsolutePath());
					Files.setAttribute(cpath, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
				}
				catch (IOException ex)
				{
					DisplayPanel.println("Failed to setup device.");
				}
				deviceSetupStatus = 0;
				break;
		}
		deviceSetupInput = "";
	}
	public static void backupRepository(File remoteBase)
	{
		File localBase = new File("data/" + remoteBase.getName());
		inCopy = localBase;
		copyFolder(remoteBase, localBase, "");
		inCopy = null;
	}
	public static void updateRemoteRepository(File remoteBase)
	{
		File localBase = new File("data/" + remoteBase.getName());
		copyFolderToRemote(localBase, remoteBase, remoteBase.getName());
	}
	public static void copyFolderToRemote(File fo, File fn, String repoName)
	{
		recursiveDelete(fn);
		fn.mkdir();
		for (File source : fo.listFiles())
		{
			String fnpath = fn.getAbsolutePath();
			String sourcepath = source.getAbsolutePath();
			File dest = new File(fnpath.substring(0, fnpath.indexOf(repoName)+repoName.length())+"\\"+sourcepath.substring(sourcepath.indexOf(repoName)+repoName.length(), sourcepath.length()));
			if (source.isDirectory())
				copyFolderToRemote(source, dest, repoName);
			else
				copyFile(source, dest);
		}
	}
	public static void copyFolder(File fo, File fn, String pathb)
	{
		recursiveDelete(fn);
		fn.mkdir();
		for (File source : fo.listFiles())
		{
			String fopath = fo.getAbsolutePath();
			String pathq = fopath.substring(fopath.indexOf(fn.getName()), fopath.length());
			File dest = new File("data/" + pathb + "/" + pathq + "/" + source.getName());
			if (source.isDirectory())
				copyFolder(source, dest, pathb + "/" + pathq);
			else
				copyFile(source, dest);
		}
		fn.setLastModified(fo.lastModified());
	}
	public static void copyFile(File source, File dest)
	{
		byte[] data = new byte[2000];
		try
		{
			FileInputStream in = new FileInputStream(source);
			if (!dest.exists())
				dest.createNewFile();
			FileOutputStream out = null;
				out = new FileOutputStream(dest);
			while (true)
			{
				int length = in.read(data);
				if (length<=0)
					break;
				out.write(data, 0, length);
			}
			in.close();
			out.close();
			dest.setLastModified(source.lastModified());
		}
		catch (IOException ex)
		{
			DisplayPanel.println("Could not copy to " + dest.getPath());
			return;
		}
	}
	public static void recursiveDelete(File f)
	{
		if (f.isDirectory())
			for (File q : f.listFiles())
				recursiveDelete(q);
		f.delete();
	}
	public static CommitInfo[] getCommitInfo(File file) throws Exception
	{
		String drive = file.getAbsolutePath().split("\\Q:\\E")[0];
		Runtime.getRuntime().exec("getcommitinfo.bat " + drive + " " + file.getAbsolutePath()).waitFor();
		Scanner in = new Scanner(new FileInputStream("inf\\commit_" + drive + ".info"));
		CommitInfo[] ret = new CommitInfo[50];
		int ind = 0;
		CommitInfo current = new CommitInfo();
		while (in.hasNext())
		{
			String inp = in.nextLine();
			if (inp.trim().equals(""))
				continue;
			if (inp.split(" ")[0].equals("commit"))
				current.commitHash = inp.split(" ")[1].trim();
			else if (inp.split(" ")[0].equals("Author:"))
				current.commitAuthor = inp.split(" ")[1].split("\\Q <\\E")[0].trim();
			else if (inp.split(" ")[0].equals("Date:"))
			{
				DateFormat dateFormat = new SimpleDateFormat("EEE MMM d H:m:s y Z");
				long time = (dateFormat.parse(inp.substring(6, inp.length()).trim()).getTime())/1000;
				current.commitTime = time;
			}
			else
			{
				current.commitName = inp.trim();
				ret[ind++] = current;
				if (ind > ret.length-1)
					break;
				current = new CommitInfo();
			}
		}
		return ret;
	}
	private static class CommitInfo
	{
		String commitName;
		String commitAuthor;
		String commitHash;
		long commitTime;
	}
}
