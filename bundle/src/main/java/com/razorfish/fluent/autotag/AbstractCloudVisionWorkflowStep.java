package com.razorfish.fluent.autotag;

import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;


import javax.jcr.Node;


import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.commons.process.AbstractAssetWorkflowProcess;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.EntityAnnotation;

public abstract class AbstractCloudVisionWorkflowStep extends AbstractAssetWorkflowProcess {

	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "Google-VisionLabelSample/1.0";
	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	public static Vision getVisionService() throws IOException, GeneralSecurityException {
		GoogleCredential credential = GoogleCredential.getApplicationDefault().createScoped(VisionScopes.all());
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		return new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, credential)
				.setApplicationName(APPLICATION_NAME).build();
	}

	/**
	 * add tag metadata to the actual asset
	 * @param workItem
	 * @param wfSession
	 * @param asset
	 * @param tagManager
	 * @param tagArray
	 * @throws Exception

	 */
	protected void addMetaData(WorkItem workItem, WorkflowSession wfSession, final Asset asset, TagManager tagManager,
			String[] tagArray) throws Exception {
		final ResourceResolver resolver = getResourceResolver(wfSession.getSession());
		final Resource assetResource = asset.adaptTo(Resource.class);
		final Resource metadata = resolver.getResource(assetResource,
				JcrConstants.JCR_CONTENT + "/" + DamConstants.METADATA_FOLDER);

		
		/*
		Tag[] existing_tags = tagManager.getTags(assetResource);
		
		if (existing_tags.length > 0) {
			String[] existing_tags_array = new String[existing_tags.length];
			int i = 0;
			for (Tag existing_tag : existing_tags) {
				log.info("existing tag " + existing_tag.getPath());
				existing_tags_array[i++] = existing_tag.getPath();
			}
			tagArray =  join(existing_tags_array, tagArray);
		} else {
			log.info("no existing tags found");
		}
		*/
	
		if (null != metadata) {
			final Node metadataNode = metadata.adaptTo(Node.class);
			
			ValueMap properties = metadata.adaptTo(ValueMap.class);
			
			String[] existing_tags = properties.get("cq:tags", String[].class);
			if (existing_tags!=null && existing_tags.length > 0) {
				log.info( existing_tags.length + " existing tags found");
				tagArray =  join(existing_tags, tagArray);
			} else {
				log.info("no existing tags found");
			}
			log.info( tagArray.length + " total tags ");
			
			metadataNode.setProperty("cq:tags",tagArray);
			
			metadataNode.getSession().save();
			log.info("added or updated tags");
		} else {
			log.warn("execute: failed setting metdata for asset [{}] in workflow [{}], no metdata node found.",
					asset.getPath(), workItem.getId());
		}
	}
	
	/**
	 * create individual tags if they don't exist yet
	 * @param tagManager
	 * @param entities
	 * @return
	 * @throws InvalidTagFormatException
	 */
	protected String[] createTags(TagManager tagManager, List<EntityAnnotation> entities, String namespace, String container)
			throws InvalidTagFormatException {
		Tag tag;
		String tagArray[] = new String[entities.size()];
		int index = 0;

		for (EntityAnnotation label : entities) {
			log.info("found label " + label.getDescription() + " with score : " + label.getScore());


			tag = tagManager.createTag(namespace+container + "/" + label.getDescription().replaceAll(" ", "_").toLowerCase(), label.getDescription(),
					"Auto detected : " + label.getDescription(), true);
			tagArray[index] = tag.getNamespace().getName() + ":" + tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
			
			log.info(tag.getNamespace().getName() + ":" + tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));

			index++;

		}
		return tagArray;
	}


	public AbstractCloudVisionWorkflowStep() {
		super();
	}

	
	/**
	 * Join two arrays
	 * @param String1
	 * @param String2
	 * @return
	 */
	protected String[] join(String[] String1, String[] String2) {
		String[] allStrings = new String[String1.length + String2.length];

		System.arraycopy(String1, 0, allStrings, 0, String1.length);
		System.arraycopy(String2, 0, allStrings, String1.length, String2.length);

		return allStrings;
	}

}