import java.io.File;

class Device
{
	public String name, dname;
	public File repoBase;
	int status = SyncStatus.WAITING_FOR_DRIVE;
	public Device(String name)
	{
		this.name = name;
		this.dname = name;
	}
}
