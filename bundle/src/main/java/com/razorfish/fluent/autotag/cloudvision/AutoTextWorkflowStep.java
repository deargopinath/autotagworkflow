package com.razorfish.fluent.autotag.cloudvision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.jcr.Node;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.osgi.framework.Constants;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.commons.jcr.JcrConstants;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.metadata.MetaDataMap;

import com.day.cq.tagging.JcrTagManagerFactory;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.Resource;
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
		@Property(name = Constants.SERVICE_DESCRIPTION, value = "GC Text - Automatic image text detection and metadata assignment."),
		@Property(name = Constants.SERVICE_VENDOR, value = "Razorfish"),
		@Property(name = "process.label", value = "GC Text - Automatic image text detection and metadata assignment") })
public class AutoTextWorkflowStep extends AbstractCloudVisionWorkflowStep {

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	private static final String NAMESPACE = "/etc/tags/cloudvision";
	private static final String CONTAINER = "/imagetext";

	private static final int MAX_LABELS = 10;
	@Reference
	JcrTagManagerFactory tmf;

	public void execute(WorkItem workItem, WorkflowSession wfSession, MetaDataMap args) throws WorkflowException {

		try {
			log.info("Autotext -  in execute method");
			final Asset asset = getAssetFromPayload(workItem, wfSession.getSession());

			// create tag manager
			TagManager tagManager = tmf.getTagManager(wfSession.getSession());
			Tag superTag = tagManager.resolve(NAMESPACE);
			Tag tag = null;

			if (superTag == null) {
				tag = tagManager.createTag(NAMESPACE + CONTAINER, "imagetext", "autodetected imagetext", true);
				log.info("Tag Name Space created : ", tag.getPath());
			} else {
				tag = superTag;
			}

			byte[] data = new byte[(int) asset.getOriginal().getSize()];
			asset.getOriginal().getStream().read(data);

			// Google cloudvision code
			Image image = new Image().encodeContent(data);
			AnnotateImageRequest request = new AnnotateImageRequest().setImage(image)
					.setFeatures(ImmutableList.of(new Feature().setType("TEXT_DETECTION").setMaxResults(MAX_LABELS)));
			Vision.Images.Annotate annotate = getVisionService().images()
					.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(request)));
			// Due to a bug: requests to Vision API containing large images fail
			// when GZipped.
			annotate.setDisableGZipContent(true);

			BatchAnnotateImagesResponse response = annotate.execute();
			List<EntityAnnotation> textAnnotations = ImmutableList.of();

			if (response.getResponses().size() != 0 && response.getResponses().get(0).getTextAnnotations() != null) {
				textAnnotations = response.getResponses().get(0).getTextAnnotations();
			}

			String document = "";

			for (EntityAnnotation text : textAnnotations) {
				document += text.getDescription();
			}
			if (document.equals("")) {
				log.info("document had no discernible text.\n");
			} else {

				log.info("autotext setting metadata" + document);
				final ResourceResolver resolver = getResourceResolver(wfSession.getSession());
				final Resource assetResource = asset.adaptTo(Resource.class);
				final Resource metadata = resolver.getResource(assetResource,
						JcrConstants.JCR_CONTENT + "/" + DamConstants.METADATA_FOLDER);

				if (null != metadata) {
					final Node metadataNode = metadata.adaptTo(Node.class);
					metadataNode.setProperty("dc:description", document);

					metadataNode.getSession().save();
					log.info("added or updated tags");
				} else {
					log.warn("execute: failed setting metdata for asset [{}] in workflow [{}], no metdata node found.",
							asset.getPath(), workItem.getId());
				}

			}

		}

		catch (Exception e) {
			log.error("Error in execution" + e);
			e.printStackTrace();
		}
	}
}