// $Id: $
/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.iosp.misc;

import ucar.nc2.*;
import ucar.nc2.iosp.AbstractIOServiceProvider;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.util.CancelTask;
import ucar.unidata.io.RandomAccessFile;
import ucar.ma2.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Date;
import java.text.ParseException;

/**
 * UspLightning3
 *
 * This looks like the most recent USPLN iosp.
 *
 * @author caron
 */
public class Uspln extends AbstractIOServiceProvider {
/*  USPLN data format:

Each 1 minute packet sent has an ASCII header, followed by a record for
each lightning detection during the past 1 minute.

Header
The ASCII header provides information on the creation time of the one
minute packet and ending date and time of the file.

Sample Header:
USPLN-LIGHTNING,2004-10-11T20:45:02,2004-10-11T20:45:02
Description:
Name of Product: USPLN-LIGHTNING
Creation of 1 min Packet (yyyy-mm-ddThh:mm:ss): 2004-10-11T20:45:02
Ending of 1 min Packet (yyyy-mm-ddThh:mm:ss): 2004-10-11T20:45:02

NOTE: All times in UTC

Strike Record Following the header, an individual record is provided for
each lightning strike in a comma delimited format.

Sample Strike Records:
2004-10-11T20:44:02,32.6785331,-105.4344587,-96.1,1
2004-10-11T20:44:05,21.2628231,-86.9596634,53.1,1
2004-10-11T20:44:05,21.2967119,-86.9702106,50.3,1
2004-10-11T20:44:06,19.9044769,-100.7082608,43.1,1
2004-10-11T20:44:11,21.4523434,-82.5202274,-62.8,1
2004-10-11T20:44:11,21.8155306,-82.6708778,80.9,1

Description:

Strike Date/Time (yyyy-mm-ddThh:mm:ss): 2004-10-11T20:44:02

Strike Latitude (deg): 32.6785331
Strike Longitude (deg): -105.4344587
Strike Amplitude (kAmps, see note below): -96.1
Stroke Count (number of strokes per flash): 1

Note: At the present time USPLN data are only provided in stroke format,
so the stroke count will always be 1.

Notes about Decoding Strike Amplitude
The amplitude field is utilized to indicate the amplitude of strokes and
polarity of strokes for all Cloud-to- Ground Strokes.

For other types of detections this field is utilized to provide
information on the type of stroke detected.

The amplitude number for Cloud-to-Ground strokes provides the amplitude
of the stroke and the sign (+/-) provides the polarity of the stroke.

An amplitude of 0 indicates USPLN Cloud Flash Detections rather than
Cloud-to-Ground detections.

Cloud flash detections include cloud-to-cloud, cloud-to-air, and
intra-cloud flashes.

An amplitude of -999 or 999 indicates a valid cloud-to-ground stroke
detection in which an amplitude was not able to be determined. Typically
these are long-range detections.
*/

  private static final String MAGIC = "USPLN-LIGHTNING";

  public boolean isValidFile(RandomAccessFile raf) throws IOException {
    raf.seek(0);
    int n = MAGIC.length();
    if (raf.length() < n) return false;

    byte[] b = new byte[n];
    raf.read(b);
    String got = new String(b);
    return got.equals(MAGIC);
  }

  private static final String DATE = "date";
  private static final String LAT = "lat";
  private static final String LON = "lon";
  private static final String AMP = "strikeAmplitude";
  private static final String NSTROKES = "strokeCount";

  private long[] offsets;
  private double lat_min, lat_max;
  private double lon_min, lon_max;
  private int time_min, time_max;

  public void open(RandomAccessFile raf, NetcdfFile ncfile, CancelTask cancelTask) throws IOException {
    this.raf = raf;

    int n;
    try {
      n = readAllData(raf);
    } catch (ParseException e) {
      e.printStackTrace();
      throw new IOException("bad data");
    }

    Dimension recordDim = new Dimension("record", n, true, true, false);
    ncfile.addDimension(null, recordDim);

    Structure struct = new Structure(ncfile, null, null, "record");
    struct.setDimensions("record");
    ncfile.addVariable(null, struct);

    Variable date = new Variable(ncfile, null, null, DATE);
    date.setDimensions("");
    date.setDataType(DataType.INT);
    String timeUnit = "seconds since 1970-01-01 00:00:00";
    date.addAttribute(new Attribute("long_name", "date of strike"));
    date.addAttribute(new Attribute("units", timeUnit));
    date.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Time.toString()));
    struct.addMemberVariable(date);
    date.setSPobject(new IospData(0));

    Variable lat = new Variable(ncfile, null, null, LAT);
    lat.setDimensions("");
    lat.setDataType(DataType.DOUBLE);
    lat.addAttribute(new Attribute("long_name", "latitude"));
    lat.addAttribute(new Attribute("units", "degrees_north"));
    lat.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Lat.toString()));
    struct.addMemberVariable(lat);
    lat.setSPobject(new IospData(1));

    Variable lon = new Variable(ncfile, null, null, LON);
    lon.setDimensions("");
    lon.setDataType(DataType.DOUBLE);
    lon.addAttribute(new Attribute("long_name", "longitude"));
    lon.addAttribute(new Attribute("units", "degrees_east"));
    lon.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Lon.toString()));
    struct.addMemberVariable(lon);
    lon.setSPobject(new IospData(2));

    Variable amp = new Variable(ncfile, null, null, AMP);
    amp.setDimensions("");
    amp.setDataType(DataType.DOUBLE);
    amp.addAttribute(new Attribute("long_name", "amplitude of strike"));
    amp.addAttribute(new Attribute("units", "kAmps"));
    amp.addAttribute(new Attribute("missing_value", new Double(999)));
    struct.addMemberVariable(amp);
    amp.setSPobject(new IospData(3));

    Variable nstrokes = new Variable(ncfile, null, null, NSTROKES);
    nstrokes.setDimensions("");
    nstrokes.setDataType(DataType.INT);
    nstrokes.addAttribute(new Attribute("long_name", "number of strokes per flash"));
    nstrokes.addAttribute(new Attribute("units", ""));
    struct.addMemberVariable(nstrokes);
    nstrokes.setSPobject(new IospData(4));

    ncfile.addAttribute(null, new Attribute("title", "USPN Lightning Data"));
    ncfile.addAttribute(null, new Attribute("history", "Read directly by Netcdf Java IOSP"));

    //ncfile.addAttribute(null, new Attribute("Conventions", "Unidata Observation Dataset v1.0"));
    ncfile.addAttribute(null, new Attribute("cdm_data_type", FeatureType.POINT.toString()));
    ncfile.addAttribute(null, new Attribute("observationDimension", "record"));

    ncfile.addAttribute(null, new Attribute("time_coverage_start", time_min + " " + timeUnit));
    ncfile.addAttribute(null, new Attribute("time_coverage_end", time_max + " " + timeUnit));

    ncfile.addAttribute(null, new Attribute("geospatial_lat_min", new Double(lat_min)));
    ncfile.addAttribute(null, new Attribute("geospatial_lat_max", new Double(lat_max)));

    ncfile.addAttribute(null, new Attribute("geospatial_lon_min", new Double(lon_min)));
    ncfile.addAttribute(null, new Attribute("geospatial_lon_max", new Double(lon_max)));

    ncfile.finish();
  }

  // 2006-10-23T17:59:39,18.415434,-93.480526,-26.8,1
  int readAllData(RandomAccessFile raf) throws IOException, NumberFormatException, ParseException {
    ArrayList offsetList = new ArrayList();

    java.text.SimpleDateFormat isoDateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    isoDateTimeFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

    lat_min = 1000.0;
    lat_max = -1000.0;
    lon_min = 1000.0;
    lon_max = -1000.0;
    time_min = Integer.MAX_VALUE;
    time_max = 0;

    raf.seek(0);
    int count = 0;
    while (true) {
      long offset = raf.getFilePointer();
      String line = raf.readLine();
      if (line == null) break;
      if (line.startsWith(MAGIC)) continue;

      StringTokenizer stoker = new StringTokenizer(line, ",\r\n");
      while (stoker.hasMoreTokens()) {
        Date d = isoDateTimeFormat.parse(stoker.nextToken());
        double lat = Double.parseDouble(stoker.nextToken());
        double lon = Double.parseDouble(stoker.nextToken());
        double amp = Double.parseDouble(stoker.nextToken());
        int nstrikes = Integer.parseInt(stoker.nextToken());

        Strike s = new Strike(d, lat, lon, amp, nstrikes);
        lat_min = Math.min(lat_min, s.lat);
        lat_max = Math.max(lat_max, s.lat);
        lon_min = Math.min(lon_min, s.lon);
        lon_max = Math.max(lon_max, s.lon);
        time_min = Math.min(time_min, s.d);
        time_max = Math.max(time_max, s.d);
      }

      offsetList.add(new Long(offset));
      count++;
    }

    offsets = new long[count];
    for (int i = 0; i < offsetList.size(); i++) {
      Long off = (Long) offsetList.get(i);
      offsets[i] = off.longValue();
    }

    System.out.println("processed " + count + " records");
    return count;
  }

  private class Strike {
    int d;
    double lat, lon, amp;
    int n;

    Strike(long offset, java.text.SimpleDateFormat isoDateTimeFormat) throws IOException, ParseException {
      raf.seek(offset);
      String line = raf.readLine();
      if ((line == null) || line.startsWith(MAGIC))
        throw new IllegalStateException();

      StringTokenizer stoker = new StringTokenizer(line, ",\r\n");
      Date d = isoDateTimeFormat.parse(stoker.nextToken());
      makeDate(d);
      lat = Double.parseDouble(stoker.nextToken());
      lon = Double.parseDouble(stoker.nextToken());
      amp = Double.parseDouble(stoker.nextToken());
      n = Integer.parseInt(stoker.nextToken());
    }

    Strike(Date d, double lat, double lon, double amp, int n) {
      makeDate(d);
      this.lat = lat;
      this.lon = lon;
      this.amp = amp;
      this.n = n;
    }

    void makeDate(Date date) {
      this.d = (int) (date.getTime() / 1000);
    }

    public String toString() {
      return lat + " " + lon + " " + amp + " " + n;
    }

  }

  private class IospData {
    int varno;

    IospData(int varno) {
      this.varno = varno;
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////

  public Array readNestedData(Variable v2, Section section) throws IOException, InvalidRangeException {
    IospData iospd = (IospData) v2.getSPobject();

    java.text.SimpleDateFormat isoDateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    isoDateTimeFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

    int[] sectionShape = section.getShape();
    Array data = Array.factory(v2.getDataType(), sectionShape);
    Index ima = data.getIndex();

    int count = 0;
    Range r = (Range) section.getRange(0);
    Range.Iterator riter = r.getIterator();
    while (riter.hasNext()) {
      int index = riter.next();
      long offset = offsets[index];
      Strike s;
      try {
        s = new Strike(offset, isoDateTimeFormat);
      } catch (ParseException e) {
        throw new IOException(e.getMessage());
      }

      ima.set(count);
      switch (iospd.varno) {
        case 0:
          data.setInt(ima, s.d);
          break;
        case 1:
          data.setDouble(ima, s.lat);
          break;
        case 2:
          data.setDouble(ima, s.lon);
          break;
        case 3:
          data.setDouble(ima, s.amp);
          break;
        case 4:
          data.setInt(ima, s.n);
          break;
      }

      count++;
    }

    return data;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////

  public Array readData(Variable v2, Section section) throws IOException, InvalidRangeException {

    java.text.SimpleDateFormat isoDateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    isoDateTimeFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

    Structure struct = (Structure) v2;

    int[] sectionShape = section.getShape();
    ArrayStructureMA structData = new ArrayStructureMA(struct.makeStructureMembers(), sectionShape);
    ArrayInt.D1 dateArray = (ArrayInt.D1) Array.factory(DataType.INT, sectionShape);
    ArrayDouble.D1 latArray = (ArrayDouble.D1) Array.factory(DataType.DOUBLE, sectionShape);
    ArrayDouble.D1 lonArray = (ArrayDouble.D1) Array.factory(DataType.DOUBLE, sectionShape);
    ArrayDouble.D1 ampArray = (ArrayDouble.D1) Array.factory(DataType.DOUBLE, sectionShape);
    ArrayInt.D1 nstrokesArray = (ArrayInt.D1) Array.factory(DataType.INT, sectionShape);

    structData.setMemberArray(DATE, dateArray);
    structData.setMemberArray(LAT, latArray);
    structData.setMemberArray(LON, lonArray);
    structData.setMemberArray(AMP, ampArray);
    structData.setMemberArray(NSTROKES, nstrokesArray);

    int count = 0;
    Range r = section.getRange(0);
    Range.Iterator riter = r.getIterator();
    while (riter.hasNext()) {
      int index = riter.next();
      long offset = offsets[index];
      try {
        Strike s = new Strike(offset, isoDateTimeFormat);
        dateArray.set(count, s.d);
        latArray.set(count, s.lat);
        lonArray.set(count, s.lon);
        ampArray.set(count, s.amp);
        nstrokesArray.set(count, s.n);
        count++;

      } catch (ParseException e) {
        throw new IOException(e.getMessage());
      }

    }

    return structData;
  }
}
