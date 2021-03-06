package net.foxopen.fox.thread.storage;

import net.foxopen.fox.ContextLabel;
import net.foxopen.fox.ContextUElem;
import net.foxopen.fox.XFUtil;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.ex.ExModule;
import net.foxopen.fox.filetransfer.UploadInfo;
import net.foxopen.fox.module.Mod;
import net.foxopen.fox.module.Validatable;

import java.sql.Blob;
import java.util.Map;


public class FileStorageLocation
extends StorageLocation
implements Validatable {

  private boolean mIsUploadTarget;

  /**
  * Constructs a FileStorageLocation from the given XML
  * specification.
  *
  * @param pDefinitionDOM the XML element that represents a <code>file-storage-location</code>.
  * @exception ExInternal thrown if an error occurs parsing the file storage location
  */
  public FileStorageLocation(Mod pMod, DOM pDefinitionDOM)
  throws ExModule {
    //Force the SL code to generate a cache key from select binds (ignore whatever cache key the user has put)
    super(pMod, pDefinitionDOM, true);

    // Parse the API statement if defined
    DOM lAPIDefinition = pDefinitionDOM.get1EOrNull("fm:api");
    if(lAPIDefinition != null) {
      DatabaseStatement lDatabaseStatement = DatabaseStatement.createFromDOM(lAPIDefinition, "fm:statement", StatementType.API, getName());
      addDatabaseStatement(StatementType.API, lDatabaseStatement);
    }

    mIsUploadTarget = Boolean.valueOf(XFUtil.nvl(pDefinitionDOM.getAttrOrNull("is-upload-target"), "true"));

    //Validate that a query or API was specified
    boolean isDMLBasedFile = pDefinitionDOM.getUL("fm:database").getLength() > 0;
    boolean isAPIBasedFile = lAPIDefinition != null;
    if (!isDMLBasedFile && !isAPIBasedFile) {
      throw new ExInternal("Error in file-storage-location "+ getName()+" - expected either a database or API driven file storage location, or both. "+
                           "Please use either a \"database\" and/or \"api\" sub-element to specify a way to query and store an uploaded file.");
    }
  }

  public FileStorageLocation(String pName, Map<StatementType, DatabaseStatement> pStatementMap) {
    super(pName, pStatementMap);
  }

  /**
   * Evaluates the binds for this storage location to create a working copy.
   * @param pContextUElem
   * @param pUploadTarget DOM node where upload metadata is stored.
   * @return
   */
  private <T> WorkingFileStorageLocation<T> createWorkingStorageLocation(Class<T> pLOBClass, ContextUElem pContextUElem, DOM pUploadTarget, boolean pReadOnly) {
    pContextUElem.localise("Create WFSL");
    try {
      //Set attach/item contexts for relative bind evaluation
      pContextUElem.setUElem(ContextLabel.ATTACH, pUploadTarget.getParentOrSelf());
      pContextUElem.setUElem(ContextLabel.ITEM, pUploadTarget);
      return new WorkingFileStorageLocation<T>(pLOBClass, this, pContextUElem, pReadOnly);
    }
    finally {
      pContextUElem.delocalise("Create WFSL");
    }
  }

  /**
   * Creates a read only WFSL for the download of a previously uploaded file.
   * @param pContextUElem For WFSL bind evaluation.
   * @param pUploadTarget DOM containing file upload metadata.
   * @return New WFSL which can be used for a download.
   */
  public WorkingFileStorageLocation<Blob> createWorkingStorageLocationForUploadDownload(ContextUElem pContextUElem, DOM pUploadTarget) {
    return createWorkingStorageLocation(Blob.class, pContextUElem, pUploadTarget, true);
  }

  /**
   * Creates a standard WFSL for either a generation destination or a download target.
   * @param <T> LOB type to be selected by the WFSL (usually Blob or Clob).
   * @param pLOBClass LOB type to be selected by the WFSL.
   * @param pContextUElem For WFSL bind evaluation.
   * @param pReadOnly If true, the created WFSL will only have SELECT bind variables evaluated, and will not be able
   * to insert/update rows.
   * @return New WFSL.
   */
  public <T> WorkingFileStorageLocation<T> createWorkingStorageLocation(Class<T> pLOBClass, ContextUElem pContextUElem, boolean pReadOnly) {
    return new WorkingFileStorageLocation<T>(pLOBClass, this, pContextUElem, pReadOnly);
  }

  /**
   * Creates a working storage location based on this storage location which can act as an upload target.
   * @param pContextUElem
   * @param pUploadTarget DOM node being uploaded into.
   * @param pUploadInfo For providing upload file metadata.
   * @return
   */
  public WorkingUploadStorageLocation createWorkingUploadStorageLocation(ContextUElem pContextUElem, DOM pUploadTarget, UploadInfo pUploadInfo) {
    pContextUElem.localise("Create WFSL");
    try {
      //Set attach/item contexts for relative bind evaluation
      pContextUElem.setUElem(ContextLabel.ATTACH, pUploadTarget.getParentOrSelf());
      pContextUElem.setUElem(ContextLabel.ITEM, pUploadTarget);
      return new WorkingUploadStorageLocation(this, pContextUElem, pUploadInfo);
    }
    finally {
      pContextUElem.delocalise("Create WFSL");
    }

  }

  public boolean isUploadTarget() {
    return mIsUploadTarget;
  }
}
