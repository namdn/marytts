/**
 * Copyright 2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package marytts.tools.voiceimport;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

import marytts.nonverbal.BackchannelUnitFileReader;
import marytts.unitselection.data.UnitFileReader;

/**
 * Back-channel unit writer
 * @author sathish pammi
 *
 */
public class BackchannelUnitfileWriter extends VoiceImportComponent
{
    protected File maryDir;
    protected String unitFileName;
    protected File unitlabelDir;
    protected int samplingRate;
    protected String pauseSymbol;
    
    protected String unitlabelExt = ".lab";
    
    protected DatabaseLayout db = null;
    protected int percent = 0;
    protected BasenameList bachChannelList;
    
    public String LABELDIR = "BackchannelUnitfileWriter.backchannelLabDir";
    public String UNITFILE = "BackchannelUnitfileWriter.unitFile";
    public String BASELIST = "BackchannelUnitfileWriter.backchannelBaseNamesList";
    
    public String getName(){
        return "BackchannelUnitfileWriter";
    }
    
    public void initialiseComp()
    {
        maryDir = new File(db.getProp(db.FILEDIR));
        
        samplingRate = Integer.parseInt(db.getProp(db.SAMPLINGRATE));
        pauseSymbol = System.getProperty("pause.symbol", "pau");
    
        unitFileName = getProp(UNITFILE);
        unitlabelDir = new File(getProp(LABELDIR));
        try {
            bachChannelList = new BasenameList( getProp(BASELIST));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public SortedMap getDefaultProps(DatabaseLayout db){
       this.db = db;
       if (props == null){
           props = new TreeMap();
           String rootDir = db.getProp(db.ROOTDIR);
           props.put(LABELDIR, rootDir
                   +"backchannel_lab"
                   +System.getProperty("file.separator"));
           props.put(UNITFILE, db.getProp(db.FILEDIR)
                   +"backchannelUnits"+db.getProp(db.MARYEXT));           
           props.put(BASELIST, "backchannel.lst");
       }
       return props;
    }
    
    protected void setupHelp(){
        props2Help = new TreeMap();
        props2Help.put(LABELDIR, "directory containing the phone labels");
        props2Help.put(UNITFILE, "file containing all phone units. Will be created by this module");           
    }
    
    public boolean compute() throws IOException
    {
        if (!unitlabelDir.exists()){
            System.out.print(LABELDIR+" "+getProp(LABELDIR)+" does not exist; ");
            throw new Error("Could not create LABELDIR");
        }  
       
        System.out.println("Back channel unitfile writer started...");
        backChannelUnits bcUnits = new backChannelUnits(unitlabelDir.getAbsolutePath(),bachChannelList);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(unitFileName)));
        long posNumUnits = new MaryHeader(MaryHeader.UNITS).writeTo(out);
        int numberOfBCUnits = bcUnits.getNumberOfUnits();
        out.writeInt(numberOfBCUnits); 
        out.writeInt(samplingRate);
        long globalStart = 0l; // time, given as sample position with samplingRate
        for(int i=0;i<numberOfBCUnits;i++){
            UnitLabel[] fileLabels =  bcUnits.getUnitLabels(i);
            double unitTimeSpan  = bcUnits.getTimeSpan(i);
            int localLabLength = fileLabels.length; 
            out.writeInt(localLabLength);
            for(int j=0;j<localLabLength;j++){
                double startTime = fileLabels[j].startTime;
                double endTime = fileLabels[j].endTime;
                double duration = endTime - startTime;
                long end = (long)( endTime * (double)(samplingRate) );
                long start = (long)( startTime * (double)(samplingRate) );
                out.writeLong( globalStart + start); out.writeInt((int) (end - start));
                out.writeInt(fileLabels[j].unitName.toCharArray().length);
                out.writeChars(fileLabels[j].unitName);
            }
            globalStart += ((long)( (double)( unitTimeSpan  ) * (double)(samplingRate) ));
        }

        out.close();
        BackchannelUnitFileReader tester = new BackchannelUnitFileReader(unitFileName);
        int unitsOnDisk = tester.getNumberOfUnits();
        if (unitsOnDisk == numberOfBCUnits) {
            System.out.println("Can read right number of units");
            return true;
        } else {
            System.out.println("Read wrong number of units: "+unitsOnDisk);
            return false;
        }
    }

    
    class backChannelUnits{
          int numberOfUnits;
          UnitLabel[][] unitLabels;
          double[] unitTimeSpan; 
          
          backChannelUnits(String unitlabelDir, BasenameList basenameList) throws IOException{
              this.numberOfUnits = basenameList.getLength();
              unitLabels = new UnitLabel[this.numberOfUnits][];
              unitTimeSpan = new double[this.numberOfUnits];
              for(int i=0; i<this.numberOfUnits; i++){
                  String fileName =  unitlabelDir+File.separator+basenameList.getName(i)+unitlabelExt;
                  unitLabels[i]   =  readLabFile(fileName);
                  unitTimeSpan[i] = unitLabels[i][unitLabels[i].length - 1].endTime;
              }
          }
        
          int getNumberOfUnits(){
              return this.numberOfUnits;
          }
          
          UnitLabel[] getUnitLabels(int i){
              return this.unitLabels[i];
          }
          
          double getTimeSpan(int i){
              return this.unitTimeSpan[i];
          }
    }
    
    class UnitLabel{
        String unitName;
        double startTime;
        double endTime;
        int unitIndex;
        public UnitLabel(String unitName, double startTime, double endTime, int unitIndex){
            this.unitName = unitName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.unitIndex = unitIndex;
        }
    }
    
    /**
     * @param labFile
     * @return
     */
    private UnitLabel[] readLabFile(String labFile) throws IOException{
        
        ArrayList<String> lines = new ArrayList<String>();
        BufferedReader labels = new BufferedReader
                            (new InputStreamReader
                                    (new FileInputStream
                                            (new File(labFile)), "UTF-8"));
        String line;
        
        // Read Label file first
        //1. Skip label file header:
        while ((line = labels.readLine()) != null) {
            if (line.startsWith("#")) break; // line starting with "#" marks end of header
        }
        
        //2. Put data into an ArrayList  
        String labelUnit = null;
        double startTimeStamp = 0.0;
        double endTimeStamp = 0.0;
        int unitIndex = 0;
        while ((line = labels.readLine()) != null) {
            labelUnit = null;
            if (line != null){
                List labelUnitData = getLabelUnitData(line);
                if(labelUnitData==null) continue;
                labelUnit = (String)labelUnitData.get(2);
                unitIndex = Integer.parseInt((String)labelUnitData.get(1));
                endTimeStamp = Double.parseDouble((String)labelUnitData.get(0)); 
            }
            if(labelUnit == null) break;
            lines.add(labelUnit.trim()+" "+startTimeStamp+" "+endTimeStamp+" "+unitIndex);
            startTimeStamp = endTimeStamp;  
        }
        labels.close();
        
        UnitLabel[] ulab = new UnitLabel[lines.size()];
        Iterator<String> itr = lines.iterator();
        for(int i=0; itr.hasNext() ; i++) {
            String element = itr.next();
            String[] wrds = element.split("\\s+");
            ulab[i] = new UnitLabel(wrds[0],
                    (new Double(wrds[1])).doubleValue(),
                    (new Double(wrds[2])).doubleValue(),
                    (new Integer(wrds[3])).intValue());
        }
        return ulab;
    }
    
    /**
     * To get Label Unit DATA (time stamp, index, phone unit)
     * @param line
     * @return ArrayList contains time stamp, index and phone unit
     * @throws IOException
     */
    private ArrayList getLabelUnitData(String line) throws IOException
    {
        if (line == null) return null;
        if (line.trim().equals("")) return null;
        ArrayList unitData = new ArrayList();
        StringTokenizer st = new StringTokenizer(line.trim());
        //the first token is the time
        unitData.add(st.nextToken()); 
        //the second token is the unit index
        unitData.add(st.nextToken());
        //the third token is the phoneme
        unitData.add(st.nextToken());
        return unitData;
    }
    
    /**
     * Provide the progress of computation, in percent, or -1 if
     * that feature is not implemented.
     * @return -1 if not implemented, or an integer between 0 and 100.
     */
    public int getProgress()
    {
        return percent;
    }

    public static void main(String[] args) throws Exception
    {
        BackchannelUnitfileWriter ufw = new BackchannelUnitfileWriter();
        new DatabaseLayout(ufw); 
        ufw.compute();
    }

}