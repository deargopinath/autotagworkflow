package com.razorfish.fluent.autotag.oxford;

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

public abstract class AbstractOxfordWorkflowStep extends AbstractAssetWorkflowProcess {

	private String _requestUri = "https://api.projectoxford.ai/";
	private static String MSFT_API_KEY_FACE = "MSFT_API_KEY_FACE";
	private static String MSFT_API_KEY_VISION = "MSFT_API_KEY_VISION";
	private String _apiKeyFace = "";
	private String _apiKeyVision = "";

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	public String getAPIKeyFace() {
		if (this._apiKeyFace.length() == 0) {
			String value = System.getenv(MSFT_API_KEY_FACE);
			this._apiKeyFace = value;
		}
		return this._apiKeyFace;
	}

	public String getAPIKeyVision() {
		if (this._apiKeyVision.length() == 0) {
			String value = System.getenv(MSFT_API_KEY_VISION);
			this._apiKeyVision = value;
		}
		return this._apiKeyVision;
	}

	private String handleRequestResponse(byte[] image, String apiKey, String method, String parameters)
			throws IOException {

		URL url = new URL(_requestUri + method + "?" + "subscription-key=" + apiKey + parameters);

		HttpURLConnection handle = (HttpURLConnection) url.openConnection();
		handle.setDoOutput(true);
		// handle.setRequestProperty("Accept", "application/json");
		handle.setRequestProperty("Content-Type", "application/octet-stream");

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

	protected String getImageTags(byte[] image) throws Exception {
		return handleRequestResponse(image, getAPIKeyVision(), "vision/v1.0/analyze",
				"&visualFeatures=Description,Tags&details=celebrities");
	}

	protected String getImageFaceTags(byte[] image) throws Exception {
		return handleRequestResponse(image, getAPIKeyFace(), "face/v1.0/detect",
				"&returnFaceId=false&returnFaceLandmarks=false&returnFaceAttributes=age,gender");
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

			log.info("found label " + label.getString("name") + " with score : " + label.getString("confidence"));

			tag = tagManager.createTag(
					namespace + container + "/" + label.getString("name").replaceAll(" ", "_").toLowerCase(),
					label.getString("name"), "Auto detected : " + label.getString("name"), true);
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
		String tagArray[] = new String[entities.length() * 2];
		int index = 0;
		log.info("Entities size {} " + entities.length());
		for (int i = 0; i < entities.length(); i++) {

			JSONObject face = (JSONObject) entities.get(i);

			JSONObject faceAttributes = (JSONObject) face.get("faceAttributes");

			tag = tagManager.createTag(
					namespace + container + "/" + faceAttributes.getString("gender").replaceAll(" ", "_").toLowerCase(),
					faceAttributes.getString("gender"), "Auto detected : " + faceAttributes.getString("gender"), true);
			tagArray[index] = tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
			log.info(tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
			index++;

			tag = tagManager.createTag(
					namespace + container + "/" + faceAttributes.getString("age").replaceAll(" ", "_").toLowerCase(),
					faceAttributes.getString("age"), "Auto detected : " + faceAttributes.getString("age"), true);
			tagArray[index] = tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1);
			log.info(tag.getNamespace().getName() + ":"
					+ tag.getPath().substring(tag.getPath().indexOf(namespace) + namespace.length() + 1));
			index++;

		}

		return tagArray;
	}

	public AbstractOxfordWorkflowStep() {
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