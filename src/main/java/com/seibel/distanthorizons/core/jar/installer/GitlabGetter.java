package com.seibel.distanthorizons.core.jar.installer;

import com.electronwill.nightconfig.core.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.net.URL;
import java.util.*;

/**
 * GitLab integration has been disabled.
 * This class is kept as a stub to maintain compatibility.
 *
 * @author coolGi
 */
public class GitlabGetter
{
private static final DhLogger LOGGER = new DhLoggerBuilder().build();

/** DH's instance of the Gitlab getter */
public static GitlabGetter INSTANCE = new GitlabGetter();

public final String projectID;
public final String GitProjID;
public ArrayList<Config> projectPipelines = new ArrayList<>();

/** Uses our projectID to init this */
public GitlabGetter()
{
this("18204078");
}

public GitlabGetter(String projectID)
{
this.projectID = projectID;
this.GitProjID = "";
LOGGER.info("GitLab integration disabled - no network calls will be made");
}

public Config getCommitInfo(String commit)
{
return null;
}

public Config getPipelineInfo(Number pipeline)
{
return null;
}

public Map<String, URL> getDownloads(Number pipeline)
{
return new HashMap<>();
}

public static URL getLatestForVersion(String mcVersion)
{
return null;
}
}
