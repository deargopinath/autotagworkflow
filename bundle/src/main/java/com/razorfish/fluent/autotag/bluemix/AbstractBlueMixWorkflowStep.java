package com.razorfish.fluent.autotag.bluemix;

import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;

import java.io.IOException;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.jcr.Node;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.commons.process.AbstractAssetWorkflowProcess;

public abstract class AbstractBlueMixWorkflowStep extends AbstractAssetWorkflowProcess {

	private String _requestUri = "http://access.alchemyapi.com/calls/";
	private static String IBM_API_KEY = "IBM_API_KEY";
	private String _apiKey = "";

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	public void LoadAPIKey() {
		String value = System.getenv(IBM_API_KEY);
		this._apiKey = value;
	}

	private String handleRequestResponse(byte[] image, String method) throws IOException {

		String parameterString = "&imagePostMode=raw" + "&outputMode=json" + "&maxRetrieve=10";

		if (this._apiKey.length() == 0) {
			LoadAPIKey();
		}
		URL url = new URL(_requestUri + method + "?" + "apikey=" + this._apiKey + parameterString);
		log.info(url.toString());

		HttpURLConnection handle = (HttpURLConnection) url.openConnection();
		handle.setDoOutput(true);

		log.debug("Image size :  {} ", image.length);
		handle.addRequestProperty("Content-Length", Integer.toString(image.length));

		DataOutputStream ostream = new DataOutputStream(handle.getOutputStream());
		ostream.write(image);
		ostream.close();

		if (handle.getResponseCode() == 200) {

			BufferedReader reader = new BufferedReader(new InputStreamReader(handle.getInputStream()));
			StringBuilder stringBuilder = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line + "\n");
			}
			return stringBuilder.toString();
		} else {
			BufferedReader reader = new BufferedReader(new InputStreamReader(handle.getErrorStream()));
			StringBuilder stringBuilder = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line + "\n");
			}
			log.error("Error result " + stringBuilder.toString());
			return "";
		}
	}

	protected String ImageGetRankedImageKeywords(byte[] image) throws Exception {
		return handleRequestResponse(image, "image/ImageGetRankedImageKeywords");
	}

	protected String ImageGetRankedImageFaceTags(byte[] image) throws Exception {
		return handleRequestResponse(image, "image/ImageGetRankedImageFaceTags");
	}

	/**
	 * add tag metadata to the actual asset
	 * 
	 * @param workItem
	 * @param wfSession
	 * @param asset
	 * @param tagManager
	 * @param tagArray
	 * @throws Exception
	 * 
	 */
	protected void addMetaData(WorkItem workItem, WorkflowSession wfSession, final Asset asset, TagManager tagManager,
			String[] tagArray) throws Exception {
		final ResourceResolver resolver = getResourceResolver(wfSession.getSession());
		final Resource assetResource = asset.adaptTo(Resource.class);
		final Resource metadata = resolver.getResource(assetResource,
				JcrConstants.JCR_CONTENT + "/" + DamConstants.METADATA_FOLDER);

		if (null != metadata) {
			final Node metadataNode = metadata.adaptTo(Node.class);

			ValueMap properties = metadata.adaptTo(ValueMap.class);

			String[] existing_tags = properties.get("cq:tags", String[].class);
			if (existing_tags != null && existing_tags.length > 0) {
				log.info(existing_tags.length + " existing tags found");
				tagArray = join(existing_tags, tagArray);
			} else {
				log.info("no existing tags found");
			}
			log.info(tagArray.length + " total tags ");

			metadataNode.setProperty("cq:tags", tagArray);

			metadataNode.getSession().save();
			log.info("added or updated tags");
		} else {
			log.warn("execute: failed setting metdata for asset [{}] in workflow [{}], no metdata node found.",
					asset.getPath(), workItem.getId());
		}
	}

	/**
	 * create individual tags if they don't exist yet
	 * 
	 * @param tagManager
	 * @param entities
	 * @return
	 * @throws InvalidTagFormatException
	 */
	protected String[] createTags(TagManager tagManager, JSONArray entities, String namespace, String container)
			throws InvalidTagFormatException, JSONException {
		Tag tag;
		String tagArray[] = new String[entities.length()];
		int index = 0;

		for (int i = 0; i < entities.length(); i++) {

			JSONObject label = (JSONObject) entities.get(i);

			log.info("found label " + label.getString("text") + " with score : " + label.getString("score"));

			tag = tagManager.createTag(
					namespace + container + "/" + label.getString("text").replaceAll(" ", "_").toLowerCase(),
					label.getString("text"), "Auto detected : " + label.getString("text"), true);
			tagArray[index] = tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);

			log.info(tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));

			index++;

		}

		return tagArray;
	}

	protected String[] createFaceTags(TagManager tagManager, JSONArray entities, String namespace, String container)
			throws InvalidTagFormatException, JSONException {
		Tag tag;
		String tagArray[] = new String[entities.length() * 4];
		int index = 0;
		log.info("Entities size {} " + entities.length());
		for (int i = 0; i < entities.length(); i++) {

			JSONObject face = (JSONObject) entities.get(i);

			JSONObject gender = (JSONObject) face.get("gender");
			JSONObject age = (JSONObject) face.get("age");

			tag = tagManager.createTag(
					namespace + container + "/" + gender.getString("gender").replaceAll(" ", "_").toLowerCase(),
					gender.getString("gender"), "Auto detected : " + gender.getString("gender"), true);
			tagArray[index] = tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
			log.info(tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
			index++;

			tag = tagManager.createTag(
					namespace + container + "/" + age.getString("ageRange").replaceAll(" ", "_").toLowerCase(),
					age.getString("ageRange"), "Auto detected : " + age.getString("ageRange"), true);
			tagArray[index] = tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
			log.info(tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
			index++;

			if (face.has("identity")) {
				JSONObject identity = (JSONObject) face.get("identity");
				tag = tagManager.createTag(
						namespace + container + "/" + identity.getString("name").replaceAll(" ", "_").toLowerCase(),
						identity.getString("name"), "Auto detected : " + identity.getString("name"), true);
				tagArray[index] = tag.getNamespace().getName() + ":"
						+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
				log.info(tag.getNamespace().getName() + ":"
						+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
				index++;
			}

			if (face.has("disambiguated")) {
				JSONObject disambiguated = (JSONObject) face.get("disambiguated");
				tag = tagManager.createTag(
						namespace + container + "/"
								+ disambiguated.getString("subType").replaceAll(" ", "_").toLowerCase(),
						disambiguated.getString("subType"), "Auto detected : " + disambiguated.getString("subType"),
						true);
				tagArray[index] = tag.getNamespace().getName() + ":"
						+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
				log.info(tag.getNamespace().getName() + ":"
						+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
				index++;
			}

		}
		String reducedArray[] = new String[index];
		System.arraycopy(tagArray, 0, reducedArray, 0, reducedArray.length);
		return reducedArray;
	}

	public AbstractBlueMixWorkflowStep() {
		super();
	}

	/**
	 * Join two arrays
	 * 
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