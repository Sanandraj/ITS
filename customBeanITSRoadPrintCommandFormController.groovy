import com.navis.argo.util.XmlUtil
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.beans.EBean
import com.navis.framework.FrameworkPropertyKeys
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudDelegate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.ui.CarinaButton
import com.navis.framework.presentation.ui.event.CarinaUIEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaEventListener
import com.navis.framework.presentation.util.FrameworkUserActions
import com.navis.framework.presentation.util.PresentationConstants
import com.navis.framework.presentation.view.FrameworkIcons
import com.navis.framework.util.ValueObject
import com.navis.framework.util.message.MessageCollector
import com.navis.road.RoadBizMetafield
import com.navis.road.RoadField
import com.navis.road.business.model.Document
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.portal.PrintRequestEntry
import com.navis.road.presentation.controller.RoadPrintCommandFormController
import com.navis.road.web.RoadGuiMetafield
import org.apache.log4j.Logger
import org.jdom.Content
import org.jdom.Element
import org.jdom.filter.ContentFilter
import org.jdom.filter.ElementFilter
import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.framework.extension.FrameworkExtensionTypes

class customBeanITSRoadPrintCommandFormController extends RoadPrintCommandFormController implements EBean{
    private CarinaButton _executeButton
    private AbstractCarinaEventListener _printButtonListener

    String getDetailedDiagnostics() {
        return "customBeanITSRoadPrintCommandFormController";
    }


    @Override
    protected void configureButtonPane() {
        super.configureButtonPane();
        this._executeButton = this.getButton(FrameworkUserActions.EXECUTE);
        if (this._executeButton != null) {
            this._executeButton.removeAllListeners();
            this._executeButton.setLabelPropertyKey(FrameworkPropertyKeys.LABEL_ACTION_PRINT);
            this._executeButton.setIconId(FrameworkIcons.PRINT_16x16_NORMAL);
        }
        this._printButtonListener = new AbstractCarinaEventListener() {
            @Override
            protected void safeActionPerformed(CarinaUIEvent carinaUIEvent) {
                customBeanITSRoadPrintCommandFormController.this.sendToGos()
                customBeanITSRoadPrintCommandFormController.this.getFormUiProcessor().hideWindow(customBeanITSRoadPrintCommandFormController.this);
            }
        }

        this._executeButton.addListener(this._printButtonListener);

    }

    private void sendToGos(){
        LOG.debug("Entered customBeanITSRoadPrintCommandFormController submit command")
        Map<String,String> parameters = new HashMap<String,String>()
        FieldChanges fcs = this.getFieldChanges(false)
        Serializable laneValue
        if (fcs.hasFieldChange(RoadGuiMetafield.LANE)) {
            laneValue = (Serializable)fcs.getFieldChange(RoadGuiMetafield.LANE).getNewValue();
            LOG.debug("Print document Lane value is - " + laneValue.toString())
            parameters.put("lane",laneValue)
        }
        PrintRequestEntry requestEntry = (PrintRequestEntry)this.getAttribute("printRequestEntry")

        if(requestEntry != null) {
            BizRequest request = new BizRequest(this.getUserContext());
            request.setParameter(RoadField.DOC_PK.toString(), requestEntry.getDocGkey());
            BizResponse response = CrudDelegate.executeBizRequest(request, "rodGetDocByGkey");
            LOG.debug("Document Response - " + response.toString())
            ValueObject valueObject = response.getValueObject("Document")
            String xmlDocument = valueObject.getFieldValue(MetafieldIdFactory.valueOf("docData"))
            parameters.put("document",xmlDocument)
            parameters.put("docType", requestEntry.getDocTypeId())
            LOG.debug("Document type - " + requestEntry.getDocTypeId())
            org.jdom.Document doc = XmlUtil.parse(xmlDocument)
            Element element =  doc.getRootElement()
            Iterator filterIterator = element.getDescendants(new ElementFilter("tranNbr"))
            if(filterIterator.hasNext()) {
                String tranNbr = ((Element) filterIterator.next()).getTextNormalize()
                LOG.debug("Tran Nbr - " + tranNbr)
                parameters.put("tranNbr", tranNbr)
                final IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler();
                Map results = new HashMap();
                MessageCollector mc = handler.executeInTransaction(this.getUserContext(),
                        FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSRoadPrintFormCallBack", parameters, results);
            }


        }
    }


    private UserContext getUserContext() {
        return this.getRequestContext().getUserContext();
    }
    private static final Logger LOG = Logger.getLogger(this.class)
}
