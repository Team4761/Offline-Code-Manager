import java.awt.Font;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;

import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

import java.awt.font.LineMetrics;

import java.io.File;

import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JComponent;

class DisplayPanel extends JComponent implements Runnable
{
	JFrame frame;
	int id;
	static final int CONSOLE_HEIGHT = 150;
	static String consoleText = "";
	static ArrayList<File> repos1 = new ArrayList<File>();
	static ArrayList<File> repos2 = new ArrayList<File>();
	static ArrayList<File> currentRepos;
	static ArrayList<Device> devices = new ArrayList<Device>();
	static boolean counter, dowarn;
	static String[] statuses = {"Waiting for drive...", "Syncing...", "Ready to remove.", "Error while syncing!"};
	static Rectangle driveSetupCloseButtonBounds = new Rectangle(0, 0, 0, 0);
	public DisplayPanel(GraphicsDevice dev, int id)
	{
		frame = new JFrame();
		Rectangle rect = dev.getDefaultConfiguration().getBounds();
		frame.setLocation(rect.x, rect.y);
		frame.setSize(rect.width, rect.height);
		frame.setUndecorated(true);
		frame.addMouseListener(new MouseAdapter(){
		public void mousePressed(MouseEvent ev)
		{
			if (ev.getX() >= 240 && ev.getX() <= 260 && ev.getY()>=20)
			{
				File repoFile = currentRepos.get((ev.getY()-20)/60);
				int offs = (ev.getY()-(ev.getY()-20)/60*60-20);
				if (offs > 20 && offs < 30)
					toggleFile(new File("data/" + repoFile.getName()+".~lock"));
				else if (offs > 40 && offs < 50)
					toggleFile(new File("data/" + repoFile.getName()+".~force"));
			}
			else if (BackupManager.drawDeviceSetupWindow)
			{
				int x = ev.getX();
				int y = ev.getY();
				System.out.println(driveSetupCloseButtonBounds + " : " + x + ", " + y);
				if (driveSetupCloseButtonBounds.x < x && driveSetupCloseButtonBounds.width+driveSetupCloseButtonBounds.x > x && driveSetupCloseButtonBounds.y < y && driveSetupCloseButtonBounds.height+driveSetupCloseButtonBounds.y > y)
					BackupManager.deviceSetupStatus = 0;
			}
		}});
		frame.addKeyListener(new KeyAdapter(){
		public void keyPressed(KeyEvent ev)
		{
			if (BackupManager.drawDeviceSetupWindow)
			{
				if (ev.getKeyCode()==KeyEvent.VK_BACK_SPACE)
				{
					if (BackupManager.deviceSetupInput.length() != 0)
						BackupManager.deviceSetupInput = BackupManager.deviceSetupInput.substring(0, BackupManager.deviceSetupInput.length()-1);
				}
				else if (ev.getKeyCode()==KeyEvent.VK_ENTER)
					BackupManager.advanceDeviceSetupState();
				else
					BackupManager.deviceSetupInput += Character.toString((char)ev.getKeyCode());
			}
		}});
		frame.setDefaultCloseOperation(3);
		frame.add(this);
		this.id = id;
		frame.setVisible(true);
		new Thread(this).start();
	}
	public void run()
	{
		while (true)
		{
			frame.repaint();
		}
	}
	public void paint(Graphics g)
	{
		g.setColor(Color.BLACK);
		if (dowarn)
		{
			g.setColor(new Color((float)Math.sin(System.nanoTime()/700000000f)/5+0.2f, (float)Math.sin(System.nanoTime()/700000000f)/5+0.2f, 0f));
			dowarn = false;
		}
		g.fillRect(0, 0, getWidth(), getHeight());
		
		drawConsole(g, consoleText, 0, getHeight()-CONSOLE_HEIGHT, getWidth(), CONSOLE_HEIGHT);

		int yy = 0;		
		Font normalFont = g.getFont();
		Font largeFont = normalFont.deriveFont(25f);
		g.setFont(largeFont);
		for (Device d : devices)
		{
			String preString = d.dname + " | ";
			
			g.setColor(Color.DARK_GRAY);
			g.fillRect(getWidth()/2-2, yy+8, g.getFontMetrics().stringWidth(preString+statuses[d.status])+4, 30);
			int yystor = yy;
			
			AnimationStatus astatus = AnimationStatus.getRepoAnimationListStatus("?"+d.name);
			if (astatus != null)
			{
				g.setColor(astatus.color);
				g.fillRect(getWidth()/2-2, yy+8, g.getFontMetrics().stringWidth(preString+statuses[d.status])+4, 30);
				astatus.update();
			}
			
			g.setColor(Color.WHITE);
			g.drawString(preString, getWidth()/2, 30+yy);
			if (d.status==1)
				g.setColor(Color.YELLOW);
			else if (d.status==2)
				g.setColor(Color.GREEN);
			else if (d.status==3)
				g.setColor(Color.RED);
			g.drawString(statuses[d.status], getWidth()/2 + g.getFontMetrics().stringWidth(preString), 30+yy);
			yy += 60;
			
			if (d.status == 2)
			{
				g.setColor(new Color(0f, 0f, 0f, 0.6f));
				g.fillRect(getWidth()/2-2, yystor+8, g.getFontMetrics().stringWidth(preString+statuses[d.status])+4, 30);
			}
		}
		g.setFont(normalFont);
		
		g.setColor(Color.WHITE);
		File f = new File("data");
		int y = 10;
		g.drawString("Tracking: ", 0, y);
		ArrayList<File> trackingRepos = ((counter=!counter)?repos1:repos2);	// so nothing needs to get reallocated (don't modify currentRepos until later so an incomplete list will never be read)
		trackingRepos.clear();
		for (File fq : f.listFiles())
		{
			if (!fq.isDirectory())
				continue;
			trackingRepos.add(fq);
			
				AnimationStatus astatus = AnimationStatus.getRepoAnimationListStatus(fq.getName());
				g.setColor(Color.DARK_GRAY);
				g.fillRect(20-2, y+10-2, 260-20+4, 50+6);
				if (astatus != null)
				{
					g.setColor(astatus.color);
					g.fillRect(20-2, y+10-2, 260-20+4, 50+6);
					astatus.update();
				}
				int ystor = y;
				
			g.setColor(Color.WHITE);		
			g.drawString(fq.getName(), 20, y+=20);
			if (astatus != null && astatus.extraValues != null && astatus.extraValues.length > 0 && astatus.extraValues[0] != null)
			{
				g.setColor(astatus.color);
				g.drawString(astatus.extraValues[0], 260+4, y);
				g.setColor(Color.WHITE);
			}
			g.drawString("Backup Locked:", 40, y+=20);
			boolean lock = BackupManager.isRepositoryLocked(fq.getName());
			boolean force = BackupManager.isRepositoryForceablyUpdatingRemote(fq.getName());
			if (force)
			{
				g.setColor(Color.YELLOW);
				g.drawString("TRUE", 200, y);
			}
			else if (lock)
			{
				g.setColor(Color.GREEN);
				g.drawString("TRUE", 200, y);
			}
			else
			{
				g.setColor(Color.RED);
				g.drawString("FALSE", 200, y);
			}
			
			g.setColor(Color.BLACK);
			int fh = g.getFontMetrics().getHeight();
			g.fillRect(240, y-fh/2, 20, 10);
			
			g.setColor(Color.WHITE);
			g.drawString("Force Remote Update:", 40, y+=20);
			if (force)
			{
				g.setColor(Color.GREEN);
				g.drawString("TRUE", 200, y);
				dowarn = true;
			}
			else
			{
				g.setColor(Color.RED);
				g.drawString("FALSE", 200, y);
			}
			
			g.setColor(Color.BLACK);
			g.fillRect(240, y-fh/2, 20, 10);
			
			if (BackupManager.inCopy != null)
				if (BackupManager.inCopy.getName().equals(fq.getName()))
				{
					g.setColor(new Color(0f, 0f, 0f, 0.6f));
					g.fillRect(20-2, ystor+10-2, 260-20+4, 50+6);
				}
			
			g.setColor(Color.WHITE);
		}
		
		if (BackupManager.drawDeviceSetupWindow)
		{
			g.setColor(new Color(0, 0, 0, 0.6f));
			g.fillRect(0, 0, getWidth(), getHeight());
			
			g.setColor(Color.DARK_GRAY);
			g.fillRect(20, 20, getWidth()-40, getHeight()-40);
			g.setColor(Color.BLACK);
			g.drawRect(20, 20, getWidth()-40, getHeight()-40);
			
			g.setFont(largeFont);
			String setupName = BackupManager.deviceSetupDrive.getAbsolutePath();
			String driveSetupString = "Drive Setup (" + setupName + ")";
			Graphics2D g2 = (Graphics2D)g;
			LineMetrics lm = largeFont.getLineMetrics(driveSetupString, g2.getFontRenderContext());
			int yq = 20+(int)Math.ceil(lm.getHeight());
			
			g.setColor(Color.BLUE);
			g.fillRect(21, 21, getWidth()-40-1, yq-10-1);
			g.setColor(Color.BLACK);
			g.drawLine(20, yq+10, getWidth()-20, yq+10);
			
			g.setColor(Color.WHITE);
			g.drawString(driveSetupString, getWidth()/2-g.getFontMetrics().stringWidth(driveSetupString)/2, yq);
			
			g.setColor(Color.RED);
			g.fillRect(getWidth()-20-8-(yq-10-8*2), 20+8, yq-10-8*2+1, yq-10-8*2+1);
			
			driveSetupCloseButtonBounds = new Rectangle(getWidth()-20-8-(yq-10-8*2), 20+8, yq-10-8*2+1, yq-10-8*2+1);
			
			yq += 10+1;
			g.setFont(normalFont);
			
			drawConsole(g, BackupManager.deviceSetupFeedback, 22, yq+1, getWidth()-40-4+1, getHeight()-40-yq-3);
			drawConsole(g, BackupManager.deviceSetupInput, 22, yq+1+getHeight()-40-yq-3+3, getWidth()-40-4+1, 20-4);
			
		}
		
		currentRepos = trackingRepos;
	}
	public static void drawConsole(Graphics g, String consoleText, int x, int y, int width, int height)
	{
		g.setColor(Color.DARK_GRAY.darker());
		g.fillRect(x, y, width, height);
		
		g.setColor(Color.LIGHT_GRAY);
		String[] lines = consoleText.split("\n");
		for (int q = lines.length-1; q >= 0; q--)
		{
			if ((lines.length-1-q)*g.getFontMetrics().getHeight() > height)
			{
				for (int i = q; i >= 0; i--)
					consoleText = consoleText.substring(consoleText.indexOf("\n")+1, consoleText.length());
				break;
			}
			int tabs = 0;
			for (int qz = 0; qz < lines[q].length(); qz++)
				if (lines[q].charAt(qz)=='\t')
					tabs++;
			g.drawString(lines[q], x+tabs*20, (y+height)-(lines.length-1-q)*g.getFontMetrics().getHeight());
		}
	}
	public static void print(String str)
	{
		System.out.print(str);
		consoleText += str;
	}
	public static void println(String str)
	{
		System.out.println(str);
		consoleText += str + "\n";
	}
	public static void toggleFile(File file)
	{
		try
		{
			if (file.exists())
				file.delete();
			else
				file.createNewFile();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	public static Device showRemovableDevice(String devName)
	{
		Device d = new Device(devName);
		devices.add(d);
		return d;
	}
	public static void hideRemovableDevice(String devName)
	{
		devices.remove(getDevice(devName));
	}
	public static void setRemovableDeviceStatus(String devName, int status)
	{
		try
		{
			getDevice(devName).status = status;
		}
		catch (NullPointerException ex)
		{
			return;
		}
	}
	public static void handleDeviceRemoval(String deviceName)
	{
		println(deviceName + " removed.");
		hideRemovableDevice(deviceName);	
	}
	public static Device getDevice(String devName)
	{
		for (int q = 0; q < devices.size(); q++)
			if (devices.get(q).name.equals(devName))
				return devices.get(q);
		return null;
	}
}
