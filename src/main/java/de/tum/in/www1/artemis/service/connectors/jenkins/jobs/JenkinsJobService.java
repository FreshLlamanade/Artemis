package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Service
@Profile("jenkins")
public class JenkinsJobService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsJobService.class);

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsServer jenkinsServer;

    public JenkinsJobService(JenkinsServer jenkinsServer) {
        this.jenkinsServer = jenkinsServer;
    }

    /**
     * Retrieves the job inside a folder job or null if it doesn't exist.
     * @param folderJobName the name of the folder job
     * @param jobName the name of the job
     * @return the job with details
     */
    public JobWithDetails getJobInFolder(String folderJobName, String jobName) {
        if (folderJobName == null || jobName == null) {
            log.warn("Cannot get the job, because projectKey {} or jobName {} is null", folderJobName, jobName);
            return null;
        }

        final var folder = getFolderJob(folderJobName);
        if (folder == null) {
            log.warn("Cannot get the job {} in folder {} because it doesn't exist.", jobName, folderJobName);
            return null;
        }

        try {
            return jenkinsServer.getJob(folder, jobName);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the folder job or null if it doesn't exist
     * @param folderName the name of the folder job
     * @return the folder job
     */
    public FolderJob getFolderJob(String folderName) {
        try {
            final var job = jenkinsServer.getJob(folderName);
            if (job == null) {
                return null;
            }

            final var folderJob = jenkinsServer.getFolderJob(job);
            if (!folderJob.isPresent()) {
                return null;
            }
            return folderJob.get();
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the xml config of the job that is inside a folder
     * @param folderName the name of the folder
     * @param jobName the name of the job
     * @return the xml document
     */
    public Document getJobConfigForJobInFolder(String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("The folder " + folderName + "does not exist.");
            }

            var xmlString = jenkinsServer.getJobXml(folder, jobName);
            return XmlFileUtils.readFromString(xmlString);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the xml config of the folder job.
     * @param folderName the name of the folder
     * @return the xml document or null if the folder doesn't exist
     * @throws IOException in case of errors
     */
    public Document getFolderConfig(String folderName) throws IOException {
        if (jenkinsServer.getJob(folderName) == null) {
            return null;
        }

        String folderXml = jenkinsServer.getJobXml(folderName);
        return XmlFileUtils.readFromString(folderXml);
    }

    /**
     * Creates a job inside a folder
     * @param jobConfig the config of the job to create
     * @param folderName the name of the folder
     * @param jobName the name of the job
     */
    public void createJobInFolder(Document jobConfig, String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("Cannot create job " + jobName + " because the folder " + folderName + " does not exist.");
            }

            String configString = XmlFileUtils.writeToString(jobConfig);
            jenkinsServer.createJob(folder, jobName, configString, useCrumb);
        }
        catch (IOException | TransformerException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the job config of a job that is inside a folder
     * @param folderName the name of the folder
     * @param jobName the name of the job
     * @return the job config as an xml document or null if the job doesn't exist
     * @throws IOException in case of errors
     */
    public Document getJobConfig(String folderName, String jobName) throws IOException {
        var job = jenkinsServer.getJob(folderName);
        if (job == null) {
            return null;
        }

        var folder = jenkinsServer.getFolderJob(job);

        String jobXml = jenkinsServer.getJobXml(folder.orNull(), jobName);
        return XmlFileUtils.readFromString(jobXml);
    }

    /**
     * Updates a job.
     * @param folderName optional folder name where the job resides
     * @param jobName the name of the job
     * @param jobConfig the updated job config
     * @throws IOException in case of errors
     */
    public void updateJob(String folderName, String jobName, Document jobConfig) throws IOException {
        try {
            String configString = XmlFileUtils.writeToString(jobConfig);

            if (folderName != null && !folderName.isEmpty()) {
                var job = jenkinsServer.getJob(folderName);
                var folder = jenkinsServer.getFolderJob(job);
                jenkinsServer.updateJob(folder.orNull(), jobName, configString, useCrumb);
            }
            else {
                jenkinsServer.updateJob(jobName, configString, useCrumb);
            }
        }
        catch (TransformerException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Updates the xml desription of the folder job.
     *
     * @param folderName the name of the folder
     * @param folderConfig the xml document of the folder
     * @throws IOException in case of errors
     */
    public void updateFolderJob(String folderName, Document folderConfig) throws IOException {
        try {
            String configString = XmlFileUtils.writeToString(folderConfig);
            jenkinsServer.updateJob(folderName, configString, useCrumb);
        }
        catch (TransformerException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
