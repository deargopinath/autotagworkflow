package com.razorfish.fluent.autotag.cloudvision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.osgi.framework.Constants;

import com.day.cq.dam.api.Asset;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.metadata.MetaDataMap;

import com.day.cq.tagging.JcrTagManagerFactory;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;

import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;

@Component

@Service

@Properties({
		@Property(name = Constants.SERVICE_DESCRIPTION, value = "GC Landmark - Automatic Landmark detection and tagging."),
		@Property(name = Constants.SERVICE_VENDOR, value = "Razorfish"),
		@Property(name = "process.label", value = "GC Landmark - Automatic Landmark detection and tagging") })
public class AutoLandMarkWorkflowStep extends AbstractCloudVisionWorkflowStep {

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	private static final String NAMESPACE = "/etc/tags/cloudvision";
	private static final String CONTAINER = "/landmark";
	private static final int MAX_LABELS = 10;
	@Reference
	JcrTagManagerFactory tmf;

	public void execute(WorkItem workItem, WorkflowSession wfSession, MetaDataMap args) throws WorkflowException {

		try {
			log.info("AutoLandMarkWorkflow - execute method"); // ensure that
																// the
																// execute
																// method is
																// invoked
			final Asset asset = getAssetFromPayload(workItem, wfSession.getSession());

			// create tag manager
			log.info("AutoLandMarkWorkflow - create tag manager");
			TagManager tagManager = tmf.getTagManager(wfSession.getSession());
			Tag superTag = tagManager.resolve(NAMESPACE + CONTAINER);
			Tag tag = null;
			if (superTag == null) {
				tag = tagManager.createTag(NAMESPACE + CONTAINER, "landmark", "autodetected landmark", true);
				log.info("Tag Name Space created : ", tag.getPath());
			} else {
				tag = superTag;
			}

			byte[] data = new byte[(int) asset.getOriginal().getSize()];
			asset.getOriginal().getStream().read(data);

			// Google Cloudvision code
			AnnotateImageRequest request = new AnnotateImageRequest().setImage(new Image().encodeContent(data))
					.setFeatures(
							ImmutableList.of(new Feature().setType("LANDMARK_DETECTION").setMaxResults(MAX_LABELS)));
			Vision.Images.Annotate annotate = getVisionService().images()
					.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(request)));
			// Due to a bug: requests to Vision API containing large images fail
			// when GZipped.
			annotate.setDisableGZipContent(true);
			// [END construct_request]

			// [START parse_response]
			BatchAnnotateImagesResponse response = annotate.execute();

			List<EntityAnnotation> landmarks = response.getResponses().get(0).getLandmarkAnnotations();

			if (landmarks != null) {

				String[] tagArray = createTags(tagManager, landmarks, NAMESPACE, CONTAINER);

				addMetaData(workItem, wfSession, asset, tagManager, tagArray);
			} else {
				log.info("No landmarks found");
			}

		}

		catch (Exception e) {
			log.error("Error in execution" + e);
			e.printStackTrace();
		}
	}

}