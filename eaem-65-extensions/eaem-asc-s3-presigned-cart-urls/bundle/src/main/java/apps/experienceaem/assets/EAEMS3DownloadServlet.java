package apps.experienceaem.assets;

import com.day.cq.dam.api.Asset;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.resourceTypes=dam:Asset",
                "sling.servlet.selectors=eaems3download",
                "sling.servlet.extensions=html"
        }
)
public class EAEMS3DownloadServlet extends SlingAllMethodsServlet {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final long GB_20 = 21474836480L;

    @Reference
    private EAEMS3Service eaems3Service;

    @Reference
    private JobManager jobManager;

    public final void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
                            throws ServletException, IOException {
        String paths = request.getParameter("paths");

        if(StringUtils.isEmpty(paths)){
            return;
        }

        ResourceResolver resolver = request.getResourceResolver();

        List<Asset> assets = eaems3Service.getAssets(resolver, paths);

        try{
            long sizeOfContents = eaems3Service.getSizeOfContents(assets);

            if(sizeOfContents > GB_20 ){
                response.sendError(403, "Requested content too large");
                return;
            }

            if(sizeOfContents < eaems3Service.getDirectDownloadLimit() ){
                response.sendRedirect(eaems3Service.getDirectDownloadUrl(assets));
                return;
            }

            String cartName = eaems3Service.getCartZipFileName(request.getUserPrincipal().getName());

            logger.debug("Creating job for cart - " + cartName + ", with assets - " + paths);

            Map<String, Object> payload = new HashMap<String, Object>();

            payload.put(EAEMCartCreateJobConsumer.CART_NAME, cartName);
            payload.put(EAEMCartCreateJobConsumer.ASSET_PATHS, paths);

            jobManager.addJob(EAEMCartCreateJobConsumer.JOB_TOPIC, payload);
        }catch(Exception e){
            logger.error("Error creating cart zip", e);
            response.sendError(500, "Error creating cart zip - " + e.getMessage());
        }
    }
}
