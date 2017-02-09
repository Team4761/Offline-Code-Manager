import java.awt.Color;

import java.util.ArrayList;

class AnimationStatus
{
	Color color, baseColor;
	int iter = 0;
	String repoName;
	String[] extraValues;
	static ArrayList<AnimationStatus> list = new ArrayList<AnimationStatus>();
	static final int MAX_ITERATIONS = 100;
	public AnimationStatus(Color baseCol, String repoName, String... extraValues)
	{
		this.baseColor = baseCol;
		this.repoName = repoName;
		color = baseColor;
		this.extraValues = extraValues;
		list.add(this);
	}
	public void update()
	{
		if (++iter >= MAX_ITERATIONS)
			list.remove(this);
		color = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int)((MAX_ITERATIONS-iter)/(float)MAX_ITERATIONS*255));
	}
	public static AnimationStatus getRepoAnimationListStatus(String name)
	{
		for (AnimationStatus status : list)
			if (status.repoName.equals(name))
				return status;
		return null;
	}
	public static void createAnimation(Color color, String repoName, String... extraValues)
	{
		AnimationStatus status = getRepoAnimationListStatus(repoName);
		if (status == null)
			new AnimationStatus(color, repoName, extraValues);
		else
		{
			status.baseColor = color;
			status.iter = 0;
		}
	}
}
