/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
