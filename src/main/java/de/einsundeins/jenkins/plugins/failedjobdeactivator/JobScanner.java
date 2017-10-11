package de.einsundeins.jenkins.plugins.failedjobdeactivator;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;

public class JobScanner {

	private Logger logger = Logger.getLogger(JobScanner.class.getName());

	// Scanner configuration
	long lastSuccessfulBuild;
	int limit;
	String regex;
	long systemtime;

	List<Job<?, ?>> detectedJobs;

	public JobScanner(long lastSuccessfulBuild, int limit, String regex) {
		this.lastSuccessfulBuild = lastSuccessfulBuild
				* Constants.DAYS_TO_64BIT_UNIXTIME;
		this.limit = limit;
		this.regex = regex;
		this.systemtime = System.currentTimeMillis();
	}

	public void startDetection() {
		this.detectedJobs = new LinkedList<>();
		boolean regexProvided = regex != null && !regex.isEmpty();
		Jenkins jenkins = Jenkins.getInstance();
		if (jenkins == null)
			return;
		for (Item item : jenkins.getAllItems()) {
			if (limit == 0)
				return;
			
			if(!(item instanceof TopLevelItem))
				continue;

			if (!(item instanceof Job))
				continue;

			if (regexProvided && !jobnameMatchesPattern(item.getName()))
				continue;

			Job<?, ?> job = (Job<?, ?>) item;
			if (jobHasNoBuildsAndExistsTooLong(job)) {
				detectedJobs.add(job);
				limit--;
				continue;
			}
			if (job.getBuilds().isEmpty())
				continue;

			if (jobHasNoSuccessfulBuilds(job)) {
				limit--;
				detectedJobs.add(job);
			}

		}
	}

	private boolean jobHasNoBuildsAndExistsTooLong(Job<?, ?> job) {

		logger.log(Level.FINEST,
				"Check if job " + job.getName() + " has no builds.");

		if (!job.getBuilds().isEmpty())
			return false;

		if (isInDeadline(job.getBuildDir().lastModified()))
			return false;

		return true;
	}

	/**
	 * Checks if the last successful build is too long ago or, if there is no
	 * successful build, if the jobs exists too long
	 */
	private boolean jobHasNoSuccessfulBuilds(Job<?, ?> job) {
		logger.log(Level.FINEST,
				"Check if job " + job.getName() + " has no successful builds.");

		Run<?, ?> lastSuccessfulBuild = job.getLastSuccessfulBuild();

		if (lastSuccessfulBuild != null
				&& isInDeadline(lastSuccessfulBuild.getTimeInMillis()))
			return false;

		Run<?, ?> firstBuild = job.getFirstBuild();

		if (lastSuccessfulBuild == null
				&& isInDeadline(firstBuild.getTimeInMillis()))
			return false;

		return true;
	}

	private boolean isInDeadline(long jobtime) {
		if ((this.systemtime - jobtime) < lastSuccessfulBuild)
			return true;

		return false;
	}

	public List<Job<?, ?>> getDetectedJobs() {
		return detectedJobs;
	}

	private boolean jobnameMatchesPattern(String jobName) {
		return Pattern.matches(regex, jobName);
	}

}
