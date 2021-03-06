package net.foxopen.fox.dom.handler;

import net.foxopen.fox.ContextLabel;
import net.foxopen.fox.XFUtil;
import net.foxopen.fox.auth.AuthUtil;
import net.foxopen.fox.database.UCon;
import net.foxopen.fox.database.UConStatementResult;
import net.foxopen.fox.database.parser.ParsedStatement;
import net.foxopen.fox.database.parser.StatementParser;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.entrypoint.FoxGlobals;
import net.foxopen.fox.entrypoint.servlets.FoxMainServlet;
import net.foxopen.fox.entrypoint.uri.RequestURIBuilder;
import net.foxopen.fox.ex.ExDB;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.ex.ExTooMany;
import net.foxopen.fox.module.Mod;
import net.foxopen.fox.thread.ActionRequestContext;
import net.foxopen.fox.thread.RequestContext;
import net.foxopen.fox.thread.StatefulXThread;
import net.foxopen.fox.thread.stack.ModuleCallStack;
import net.foxopen.fox.thread.stack.ModuleStateChangeListener;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;


public class SysDOMHandler
implements DOMHandler, ModuleStateChangeListener {

  private static final ParsedStatement SYSDATE_PARSED_STATEMENT = StatementParser.parseSafely(
    "SELECT " +
    "  TO_CHAR(sysdate,'YYYY-MM-DD') \"sysdate\"" +
    ", TO_CHAR(sysdate,'YYYY-MM-DD\"T\"HH24:MI:SS') \"sysdatetime\"" +
    "FROM dual"
  , "SYS DOM sysdate select"
  );

  private final StatefulXThread mThread;

  private final DOM mSysDOM;

  public static SysDOMHandler createSysDOMHandler(RequestContext pRequestContext, StatefulXThread pThread) {
    return new SysDOMHandler(pThread, pRequestContext.createURIBuilder());
  }

  private SysDOMHandler(StatefulXThread pThread, RequestURIBuilder pRequestURIBuilder) {
    mThread = pThread;
    mThread.getModuleCallStack().registerStateChangeListener(this);

    mSysDOM = DOM.createDocument(ContextLabel.SYS.asString());
    mSysDOM.getDocControl().setDocumentReadWriteAutoIds();

    mSysDOM.getCreate1ENoCardinalityEx("thread/ref").setText(pThread.getThreadRef());
    mSysDOM.getCreate1ENoCardinalityEx("thread/app_mnem").setText(pThread.getThreadAppMnem());
    mSysDOM.getCreate1ENoCardinalityEx("thread/thread_id").setText(pThread.getThreadId());
    mSysDOM.getCreate1ENoCardinalityEx("thread/session_id").setText(pThread.getUserThreadSessionId());

    mSysDOM.getCreate1ENoCardinalityEx("engine/release").setText(FoxGlobals.getInstance().getEngineVersionInfo().getVersionNumber());
    mSysDOM.getCreate1ENoCardinalityEx("engine/build-tag").setText(FoxGlobals.getInstance().getEngineVersionInfo().getBuildTag());
    mSysDOM.getCreate1ENoCardinalityEx("engine/build-time").setText(FoxGlobals.getInstance().getEngineVersionInfo().getBuildTime());
    mSysDOM.getCreate1ENoCardinalityEx("engine/fox_services").setText(FoxGlobals.getInstance().getFoxBootConfig().getFoxServiceList());

    DOM lFoxServiceList = mSysDOM.getCreate1ENoCardinalityEx("engine/fox_service_list");
    String[] lFoxServices = FoxGlobals.getInstance().getFoxBootConfig().getFoxServiceList().split(",");
    for(int i=0; i < lFoxServices.length; i++) {
      if(XFUtil.exists(lFoxServices[i].trim())){
        lFoxServiceList.addElem("fox_service", lFoxServices[i].trim());
      }
    }

    String lStatus = FoxGlobals.getInstance().getFoxBootConfig().isProduction() ? "PRODUCTION" : "DEVELOPMENT";
    mSysDOM.getCreate1ENoCardinalityEx("engine/status").setText(lStatus);

    //TODO PN XTHREAD - logout URLs (code copied from previous XThread)
//    mSysDOM.getCreate1ENoCardinalityEx("portal_urls/logout_url").setText(mThreadDOM.get1SNoEx("logout_url"));
//    mSysDOM.getCreate1ENoCardinalityEx("portal_urls/return_url").setText(mThreadDOM.get1SNoEx("return_url"));

    //Add a URL pointing to the main FOX servlet entry point - used by edge cases such as the payment module which needs to tell the user how to get back to FOX
    refreshEngineURL(pRequestURIBuilder);

    try {
      mSysDOM.getCreate1ENoCardinalityEx("host/hostname").setText(InetAddress.getLocalHost().getHostName());
      mSysDOM.getCreate1ENoCardinalityEx("host/address").setText(InetAddress.getLocalHost().getHostAddress());
    }
    catch (UnknownHostException ex) {
      mSysDOM.getCreate1ENoCardinalityEx("host/hostname").setText("unknown");
      mSysDOM.getCreate1ENoCardinalityEx("host/address").setText("unknown");
    }

    refreshStateInfo(pThread.getModuleCallStack());
    refreshModuleInfo(pThread.getModuleCallStack());
  }

  /**
   * Refreshes the DOM's engine URL element.
   * @param pRequestURIBuilder For generating the absolute URI.
   */
  private void refreshEngineURL(RequestURIBuilder pRequestURIBuilder) {
    String lEngineURL = pRequestURIBuilder.buildServletURI(FoxMainServlet.SERVLET_PATH);
    mSysDOM.getCreate1ENoCardinalityEx("portal_urls/engine_url").setText(pRequestURIBuilder.convertToAbsoluteURL(lEngineURL));
  }

  /**
   * Refreshes the DOM's client info element.
   * @param pRequestContext For getting the client request object and finding client info.
   */
  private void refreshRequestInfo(RequestContext pRequestContext) {
    HttpServletRequest lHttpRequest = pRequestContext.getFoxRequest().getHttpRequest();

    mSysDOM.getCreate1ENoCardinalityEx("request_info/http_method").setText(lHttpRequest.getMethod());
    mSysDOM.getCreate1ENoCardinalityEx("request_info/request_uri").setText(lHttpRequest.getRequestURI());
    mSysDOM.getCreate1ENoCardinalityEx("request_info/remote_address").setText(lHttpRequest.getRemoteAddr());
    mSysDOM.getCreate1ENoCardinalityEx("request_info/forwarded_for").setText(lHttpRequest.getHeader(AuthUtil.X_FORWARDED_FOR_HEADER_NAME));
    mSysDOM.getCreate1ENoCardinalityEx("request_info/user_agent").setText(lHttpRequest.getHeader("user-agent"));
    mSysDOM.getCreate1ENoCardinalityEx("request_info/referer").setText(lHttpRequest.getHeader("referer"));

    String lQS = XFUtil.nvl(lHttpRequest.getQueryString());
    String lQueryString = lQS.substring(0, Math.min(lQS.length(), 4000));
    mSysDOM.getCreate1ENoCardinalityEx("request_info/query_string").setText(lQueryString);
  }

  @Override
  public void open(ActionRequestContext pRequestContext) {

    //Engine access URL may change throughout a thread's life (i.e. if bootstrapped on a different app server) - refresh it every churn
    refreshEngineURL(pRequestContext.createURIBuilder());

    // Refresh information about the request every churn
    refreshRequestInfo(pRequestContext);

    //Refresh sysdate and sysdatetime components
    try {
      UCon lUCon = pRequestContext.getContextUCon().getUCon("Sys DOM");
      try {
        UConStatementResult lSysdateQueryResult = lUCon.querySingleRow(SYSDATE_PARSED_STATEMENT);
        for(String lColName : lSysdateQueryResult.getColumnNames()) {
          mSysDOM.getCreate1ENoCardinalityEx("database/" + lColName).setText(lSysdateQueryResult.getString(lColName));
        }

        mSysDOM.getCreate1ENoCardinalityEx("database/name").setText(lUCon.getDatabaseName());
      }
      finally {
        pRequestContext.getContextUCon().returnUCon(lUCon, "Sys DOM");
      }
    }
    catch (ExDB e) {
      throw new ExInternal("Failed to retrieve sysdate for sys DOM", e);
    }

  }

  @Override
  public DOM getDOM() {
    return mSysDOM;
  }


  private void refreshStateInfo(ModuleCallStack pModuleCallStack) {

    if(pModuleCallStack.getStackSize() > 0 && pModuleCallStack.getTopModuleCall().getTopState() != null){
      try {
        mSysDOM
         .getCreate1E("state")
           .getCreate1E("name").setText(pModuleCallStack.getTopModuleCall().getTopState().getName()).getParentOrNull()
           .getCreate1E("title").setText(pModuleCallStack.getTopModuleCall().getTopState().getTitle());
      }
      catch (ExTooMany e) {}
    }
  }

  private void refreshModuleInfo(ModuleCallStack pModuleCallStack) {

    if(pModuleCallStack.getStackSize() > 0){

      Mod lPreviousModule = pModuleCallStack.getPreviousModuleOrNull();
      String lPreviousModuleName = "";
      String lPreviousModuleTitle = "Start";
      if(lPreviousModule != null){
        lPreviousModuleName = lPreviousModule.getName();
        lPreviousModuleTitle = lPreviousModule.getTitle();
      }

      try {
        mSysDOM
         .getCreate1E("module")
           .getCreate1E("name").setText(pModuleCallStack.getTopModuleCall().getModule().getName()).getParentOrNull()
           .getCreate1E("title").setText(pModuleCallStack.getTopModuleCall().getModule().getTitle()).getParentOrNull()
           .getCreate1E("application-title").setText(pModuleCallStack.getTopModuleCall().getModule().getHeaderControlAttribute("fm:application-title"));
        mSysDOM
          .getCreate1E("thread")
            .getCreate1E("call_id").setText(pModuleCallStack.getTopModuleCall().getCallId());
        mSysDOM
         .getCreate1E("previous_module")
           .getCreate1E("name").setText(lPreviousModuleName).getParentOrNull()
           .getCreate1E("title").setText(lPreviousModuleTitle);
        mSysDOM
         .getCreate1E("theme")
           .getCreate1E("name").setText(pModuleCallStack.getTopModuleCall().getEntryTheme().getName());
      }
      catch (ExTooMany e) {}
    }

  }

  @Override
  public void close(ActionRequestContext pRequestContext) {
  }

  @Override
  public boolean isTransient(){
    return false;
  }

  public String getContextLabel() {
    return ContextLabel.SYS.asString();
  }

  @Override
  public void handleStateChange(RequestContext pRequestContext, EventType pEventType, ModuleCallStack pCallStack) {

    if(pEventType == ModuleStateChangeListener.EventType.MODULE) {
      refreshModuleInfo(pCallStack);
      refreshStateInfo(pCallStack);
    }
    else if(pEventType == ModuleStateChangeListener.EventType.STATE) {
      refreshStateInfo(pCallStack);
    }

  }

  public void addInfo(String pPath, String pContent) {
    try {
      mSysDOM.getCreate1E(pPath).setText(pContent);
    }
    catch (ExTooMany e) {
      throw new ExInternal("Too many elements encountered when attempting to add Sys DOM info, check path (" + pPath + ")", e);
    }
  }

  @Override
  public int getLoadPrecedence() {
    return LOAD_PRECEDENCE_MEDIUM;
  }
}
