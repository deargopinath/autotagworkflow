package com.razorfish.fluent.autotag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.jcr.Node;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import javax.jcr.Node;
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

//This is a component so it can provide or consume services
@Component

@Service

@Properties({ @Property(name = Constants.SERVICE_DESCRIPTION, value = "Automatic label detection and tagging."),
		@Property(name = Constants.SERVICE_VENDOR, value = "Razorfish"),
		@Property(name = "process.label", value = "Automatic label detection and tagging") })
public class AutoLabelWorkflowStep extends AbstractCloudVisionWorkflowStep {

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	private static final String NAMESPACE = "/etc/tags/cloudvision";
	private static final String CONTAINER = "/label";
	private static final int MAX_LABELS = 3;
	@Reference
	JcrTagManagerFactory tmf;

	public void execute(WorkItem workItem, WorkflowSession wfSession, MetaDataMap args) throws WorkflowException {

		try {
			log.info("Autolabel workflow step in execute method"); 
			final Asset asset = getAssetFromPayload(workItem, wfSession.getSession());

			// create tag manager
			TagManager tagManager = tmf.getTagManager(wfSession.getSession());
			Tag superTag = tagManager.resolve(NAMESPACE+CONTAINER);
			Tag tag = null;

			if (superTag == null) {
				tag = tagManager.createTag(NAMESPACE+CONTAINER, "labels", "autodetected labels", true);
				log.info("Tag Name Space created : ", tag.getPath());
			} else {
				tag = superTag;
			}

			byte[] data = new byte[(int) asset.getOriginal().getSize()];
			asset.getOriginal().getStream().read(data);

			Image image = new Image().encodeContent(data);

			// Google Cloudvision code
			AnnotateImageRequest request = new AnnotateImageRequest().setImage(image)
					.setFeatures(ImmutableList.of(new Feature().setType("LABEL_DETECTION").setMaxResults(MAX_LABELS)));
			Vision.Images.Annotate annotate = getVisionService().images()
					.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(request)));
			// Due to a bug: requests to Vision API containing large images fail
			// when GZipped.
			annotate.setDisableGZipContent(true);
			// [END construct_request]

			// [START parse_response]
			BatchAnnotateImagesResponse response = annotate.execute();
			List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();


			String[] tagArray = createTags(tagManager, labels,NAMESPACE, CONTAINER);
			
			addMetaData(workItem, wfSession, asset, tagManager, tagArray);
			
		}

		catch (Exception e) {
			log.error("Error in execution" + e);
			e.printStackTrace();
		}
	}

	}