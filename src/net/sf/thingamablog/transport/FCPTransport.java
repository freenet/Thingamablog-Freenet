/*
 * FCPTransport.java
 *
 * Created on 13 février 2008, 02:59
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.thingamablog.transport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.plaf.ProgressBarUI;
import net.sf.thingamablog.blog.PublishProgress;

import net.sf.thingamablog.util.freenet.fcp.Client;
import net.sf.thingamablog.util.freenet.fcp.ClientPutComplexDir;
import net.sf.thingamablog.util.freenet.fcp.Connection;
import net.sf.thingamablog.util.freenet.fcp.DirectFileEntry;
import net.sf.thingamablog.util.freenet.fcp.DiskFileEntry;
import net.sf.thingamablog.util.freenet.fcp.FileEntry;
import net.sf.thingamablog.util.freenet.fcp.Message;
import net.sf.thingamablog.util.freenet.fcp.Verbosity;
import net.sf.thingamablog.util.freenet.fcp.fcpManager;
import net.sf.thingamablog.util.string.ASCIIconv;
import thingamablog.l10n.i18n;

/**
 * There is *a lot* of code below that comes from jSite
 * @author dieppe
 */
public class FCPTransport implements PublishTransport {
    private static Logger logger = Logger.getLogger("net.sf.thingamablog.transport");
    private fcpManager Manager = new fcpManager();
    private String insertURI;
    private Client client;
    private String failMsg;
    private int edition;
    private String hostname;
    private int port;
    private boolean activeLink;
    private String activeLinkPath;
    private String SSKPath;
    
    /**
     * Connects the transport
     *
     * @return true on success, false otherwise
     */
    public boolean connect(){
        failMsg="";
        if(Manager.isConnected()){
            failMsg="Already connected";
            return false;
        }
        try {
            logger.info("Connecting to the node...");
            Manager.getConnection().connect();
            client = new Client(Manager.getConnection());
            logger.info("Connected!");
            return true;
        } catch (IOException ioe) {
            failMsg="Unable to connect to the node : " + ioe.getMessage();
            logger.log(Level.WARNING,failMsg);
            ioe.printStackTrace();
        }
        return false;
    }
    
    /**
     * Disconnects the transport
     *
     * @return true on success, false otherwise
     */
    public boolean disconnect(){
        if (client.isDisconnected())
            return true;
        logger.info("Disconnecting from the node...");
        Manager.getConnection().disconnect();
        logger.info("Disconnected!");
        return true;
    }
    
    /**
     * Indicates if the transport is connected
     *
     * @return true if connected, false if not
     */
    public boolean isConnected(){
        return Manager.isConnected();
    }
    
    /**
     * Returns the reason the connect or publishFile returned false.. i.e failed
     *
     * @return The reason for failing
     */
    public String getFailureReason(){
        return failMsg;
    }
    
    public boolean publishFile(String pubPath, File file, TransportProgress tp) {
        logger.log(Level.WARNING,"You shouldn't be here! Only complete dir are publish with fcp.");
        return false;
    }
    
    public boolean publishFile(Hashtable ht, PublishProgress tp, String frontPage, String arcPath){
        //We do the publish job for an entire directory
        if(!Manager.isConnected()){
            logger.log(Level.WARNING,"The connection to the node is not open !");
            failMsg="Not connected";
            return false;
        }
        System.out.println("Beginning of the publish process...");
        int current_edition = edition + 1;
        String dirURI = "freenet:USK@" + insertURI + "/" + ASCIIconv.convertNonAscii(this.SSKPath) + "/" + current_edition + "/";
        System.out.println("Insert URI : " + dirURI);
        ClientPutComplexDir putDir = new ClientPutComplexDir("Thingamablog-insert", dirURI);
        System.out.println("Default name : " + frontPage);
        putDir.setDefaultName(frontPage);
        putDir.setMaxRetries(-1);
        putDir.setVerbosity(Verbosity.ALL);
        for(Enumeration e = ht.keys() ; e.hasMoreElements() ;) {
            Object element = e.nextElement();
            File file = (File)element;
            long[] fileLength = new long[1];
            try {
                InputStream fileEntryInputStream = createFileInputStream(file, fileLength);
                FileEntry fileEntry = createDirectFileEntry(file.getName(), fileEntryInputStream, fileLength);           
                if (fileEntry != null) {
                    System.out.println("File to insert : " + fileEntry.getFilename());
                    putDir.addFileEntry(fileEntry);
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, ex.getMessage());
            }
        }
        // If there is an active link set, we publish it
        if (activeLink) {
            File file = new File(activeLinkPath);            
            long[] fileLength = new long[1];
            InputStream fileEntryInputStream;
            try {
                fileEntryInputStream = createFileInputStream(file, fileLength);           
                FileEntry fileEntry = createDirectFileEntry("activelink.png", fileEntryInputStream, fileLength);
                if (fileEntry != null) {
                    System.out.println("File to insert : activelink.png");
                    putDir.addFileEntry(fileEntry);
                }
             } catch (IOException ex) {
                    logger.log(Level.WARNING, ex.getMessage());
             }
        }
        try {            
            client.execute(putDir);
            System.out.println("Publish in progress...");
        } catch (IOException ioe) {
            logger.log(Level.WARNING,"Publish process failed : " + ioe.getMessage());
            return false;
        }
        String finalURI = null;
        boolean success = false;
        boolean finished = false;
        boolean disconnected = false;
        int totalBlockToPublish = 0;
        int blockPublished = 0;
        tp.publishStarted(totalBlockToPublish);
        while (!finished) {
            Message message = client.readMessage();
            finished = (message == null) || (disconnected = client.isDisconnected());
            logger.log(Level.INFO, "Message from the node :" + message);
            if (!finished) {
                String messageName = message.getName();
                if ("URIGenerated".equals(messageName)) {
                    finalURI = message.get("URI");
                }
                if ("SimpleProgress".equals(messageName)) {
                    int total = Integer.parseInt(message.get("Total"));
                    int succeeded = Integer.parseInt(message.get("Succeeded"));
                    int fatal = Integer.parseInt(message.get("FatallyFailed"));
                    int failed = Integer.parseInt(message.get("Failed"));
                    boolean finalized = Boolean.valueOf(message.get("FinalizedTotal")).booleanValue();
                    blockPublished = succeeded;
                    if (totalBlockToPublish != total) {
                        totalBlockToPublish = total;
                    }
                    String log = "";
                    log += "Total : " + total;
                    log += "; Succeeded : " + succeeded;
                    log += "; Fatally failed : " + fatal;
                    log += "; Failed : " + failed;
                    log += "; Final ? " + (finalized ? "yes;" : "no;");
                    tp.logMessage(log);
                    tp.updateBlocksTransferred(blockPublished, totalBlockToPublish, i18n.str("insertion_in_progress"));
                }
                success = "PutSuccessful".equals(messageName);
                finished = success || "PutFailed".equals(messageName) || messageName.endsWith("Error");
                if (tp.isAborted())
                    disconnect();
            }            
        }
        // If the publish has been made, we update the edition number to the current edition
        if(finalURI != null){
            edition++;
        }
        tp.logMessage("URI : " + finalURI);
        return success;
    }
    
    private FileEntry createDirectFileEntry(String filename, InputStream fileEntryInputStream, long[] fileLength){
        String content = DefaultMIMETypes.guessMIMEType(filename);
        FileEntry fileEntry = new DirectFileEntry(filename, content, fileEntryInputStream, fileLength[0]);
        return fileEntry;
    }
        
    private InputStream createFileInputStream(File file, long[] length) throws IOException {
        length[0] = file.length();
        return new FileInputStream(file);
    }
    
    public void setNode(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        Manager.setNode(hostname,port);
    }
    
    public int getPort(){
        return this.port;
    }
    
    public String getHostname(){
        return this.hostname;
    }
    
    public String getInsertURI(){
        return this.insertURI;
    }
    
    public void setInsertURI(String insertURI){
        this.insertURI=shortenURI(insertURI);
    }
    
    public void setEdition(int edition){
        this.edition=edition;
    }
    
    public int getEdition(){
        return this.edition;
    }
    
    public void setActiveLink(boolean b){
        this.activeLink = b;
    }
    
    public boolean getActiveLink(){
        return this.activeLink;
    }
    
    public void setActiveLinkPath(String activeLinkPath){
        this.activeLinkPath = activeLinkPath;
    }
    
    public String getActiveLinkPath(){
        return this.activeLinkPath;
    }
    
    public String getSSKPath(){
        return this.SSKPath;
    }
    
    public void setSSKPath(String path){
        this.SSKPath = ASCIIconv.convertNonAscii(path);
    }
    
    private String shortenURI(String uri) {
        if (uri.startsWith("freenet:")) {
            uri = uri.substring("freenet:".length());
        }
        if (uri.startsWith("SSK@")) {
            uri = uri.substring("SSK@".length());
        }
        if (uri.startsWith("USK@")) {
            uri = uri.substring("USK@".length());
        }
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length()-1);
        }
        return uri;
    }
}
