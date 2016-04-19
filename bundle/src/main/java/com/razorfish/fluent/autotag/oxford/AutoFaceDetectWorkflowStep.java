package com.razorfish.fluent.autotag.oxford;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.osgi.framework.Constants;

import com.day.cq.dam.api.Asset;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.tagging.JcrTagManagerFactory;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;

@SuppressWarnings("unused")
@Component

@Service

@Properties({
		@Property(name = Constants.SERVICE_DESCRIPTION, value = "Oxford face - Automatic face detection and tagging using bluemix."),
		@Property(name = Constants.SERVICE_VENDOR, value = "Razorfish"),
		@Property(name = "process.face", value = "Oxford face - Automatic face detection and tagging using MSFT oxford") })
public class AutoFaceDetectWorkflowStep extends AbstractOxfordWorkflowStep {

	/** Default log. */
	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	private static final String NAMESPACE = "/etc/tags/msft";
	private static final String CONTAINER = "/face";
	private static final int MAX_FACES = 10;
	@Reference
	JcrTagManagerFactory tmf;

	public void execute(WorkItem workItem, WorkflowSession wfSession, MetaDataMap args) throws WorkflowException {

		try {
			log.info("Auto face MSFT workflow step in execute method");
			final Asset asset = getAssetFromPayload(workItem, wfSession.getSession());

			// create tag manager
			TagManager tagManager = tmf.getTagManager(wfSession.getSession());
			Tag superTag = tagManager.resolve(NAMESPACE + CONTAINER);
			Tag tag = null;

			if (superTag == null) {
				tag = tagManager.createTag(NAMESPACE + CONTAINER, "faces", "autodetected faces", true);
				log.info("Tag Name Space created : ", tag.getPath());
			} else {
				tag = superTag;
			}

			byte[] data = new byte[(int) asset.getOriginal().getSize()];
			int numbytesread = asset.getOriginal().getStream().read(data);

			String s = getImageFaceTags(data);

			if (s.length() > 0) {
				JSONArray face = new JSONArray(s);

				String[] tagArray = createFaceTags(tagManager, face, NAMESPACE, CONTAINER);

				addMetaData(workItem, wfSession, asset, tagManager, tagArray);
			}

		}

		catch (Exception e) {
			log.error("Error in execution" + e);
			e.printStackTrace();
		}
	}

}