package com.hujian.mp4;

import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import org.bytedeco.javacpp.opencv_highgui;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.bytedeco.javacpp.opencv_highgui.*;

/**
 * Created by hujian on 2017/4/7.
 */
public class Mp4Handler implements Serializable {

    /**
     * the video file-path
     */
    private String videoFile = null;

    private String outputVideoBase = "mp4\\";

    /**
     * the video fps
     */
    private Double videoFPS = null;

    /**
     * total frames of this video
     */
    private Long totalFrames = null;

    /**
     * how many small video you want to output
     */
    private Integer cutCount = null;

    /**
     * how long this video(s)
     */
    private Long videoTimeLength = null;

    /**
     * how many frames each small video
     */
    private Long perVideoFrames = null;

    /**
     * each small video's time
     */
    private Integer perVideoSeconds = null;

    /**
     * the small video range
     */
    private List<VideoTimeRange> videoTimeRanges = null;

    /**
     * the constructor
     * @param videoFile the source video path
     * @param cutCount  the small video count
     */
    public Mp4Handler(String videoFile,int cutCount){
        this.videoFile = videoFile;
        this.cutCount = cutCount;
        //get the fps of this video
        opencv_highgui.CvCapture cvCapture = cvCreateFileCapture(videoFile);
        if (cvCapture !=null) {
           this.videoFPS = cvGetCaptureProperty(cvCapture, CV_CAP_PROP_FPS);
        }else{
            System.out.println("get the video's fps failed");
        }
        //get the total frames
        this.totalFrames = (long) cvGetCaptureProperty(cvCapture, CV_CAP_PROP_FRAME_COUNT);
        //release the resources
        cvReleaseCapture(cvCapture);
        System.out.println("get the video's fps:"+this.videoFPS+" total frames:"+this.totalFrames);
        //get the video length
        double tmp = this.totalFrames % this.videoFPS;

        if( tmp == 0 ){
            this.videoTimeLength = (long)(this.totalFrames / this.videoFPS);
        }else{
            this.videoTimeLength = (long)(this.totalFrames / this.videoFPS + 1);
        }

        System.out.println("get video length:"+this.videoTimeLength+" (s)");

        if( this.videoTimeLength == 0 ){
            System.out.println("the video is empty,exit(0)");
            System.exit(0);
        }

        //get the per-small video frames
        tmp = this.videoTimeLength % this.cutCount;

        if( tmp == 0 ){
            this.perVideoSeconds = (int)(this.videoTimeLength / this.cutCount);
        }else{
            this.perVideoSeconds = (int)(this.videoTimeLength / this.cutCount + 1);
        }

        System.out.println("get per small video length:"+ this.perVideoSeconds + "(s)");

        if( this.perVideoSeconds == 0 ){
            System.out.println("get per small video's length is 0,exit(0)");
            System.exit(0);
        }

        //ok,get the per small video range
        this.videoTimeRanges = new ArrayList<VideoTimeRange>();

        VideoTimeRange videoTimeRange = null;
        long start = 0,end = perVideoSeconds,count = 0;
        while( end <= this.videoTimeLength ){
            videoTimeRange = new VideoTimeRange(start,end);

            System.out.println("get a split time range:"+videoTimeRange.toString());

            count ++;
            videoTimeRanges.add( videoTimeRange );
            if( end == this.videoTimeLength ){
                break;
            }
            start = end;
            end += this.perVideoSeconds;
            if( end > this.videoTimeLength ){
                end = this.videoTimeLength;
            }
        }

        if( count != this.cutCount ){
            System.out.println("real cut time:"+count+" set:"+this.cutCount);
        }

        System.out.println("initialize done.");
    }

    /**
     * do split
     * @throws IOException
     */
    public void split() throws IOException {

        if( this.videoTimeRanges == null || this.videoTimeRanges.size() == 0 ){
            System.out.println("the job is empty");
            return;
        }
        String dstFile = null;
        for( VideoTimeRange timeRange: this.videoTimeRanges ){
            dstFile = this.outputVideoBase + timeRange.getStart()+ ".mp4";
            System.out.println("generating the video:"+dstFile);
            this.SplitVideo(new File(dstFile),(int)(timeRange.getStart() * 1000),(int)(timeRange.getEnd() * 1000));
            System.out.println(timeRange.toString());
        }

        System.out.println("split video done.");
    }

    /**
     * merge videos to one
     * @param videos
     */
    public void Merge(String[] videos,String dst) throws IOException {
        this.MergeVideo(videos,dst);
    }

    /**
     * do split work
     * @param dst output file.
     * @param startMs
     * @param endMs
     * @throws IOException
     */
    private void SplitVideo(File dst, int startMs, int endMs) throws IOException {
        Movie movie  = MovieCreator.build(this.videoFile);
        // remove all tracks we will create new tracks from the old
        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());
        for (Track track : tracks) {
            printTime(track);
        }
        double startTime = startMs/1000;
        double endTime = endMs/1000;
        boolean timeCorrected = false;
        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)
                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                }
                startTime = correctTimeToSyncSample(track, startTime, false);//true
                endTime = correctTimeToSyncSample(track, endTime, true);//false
                timeCorrected = true;
            }
        }
        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            long startSample = -1;
            long endSample = -1;
            for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
                TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
                for (int j = 0; j < entry.getCount(); j++) {
                    // entry.getDelta() is the amount of time the current sample covers.
                    if (currentTime <= startTime) {
                        // current sample is still before the new starttime
                        startSample = currentSample;
                    }
                    if (currentTime <= endTime) {
                        // current sample is after the new start time and still before the new endtime
                        endSample = currentSample;
                    } else {
                        // current sample is after the end of the cropped video
                        break;
                    }
                    currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
            }
            movie.addTrack(new CroppedTrack(track, startSample, endSample));
            break;
        }
        Container container = new DefaultMp4Builder().build(movie);
        if (!dst.exists()) {
            dst.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(dst);
        FileChannel fc = fos.getChannel();
        container.writeContainer(fc);
        fc.close();
        fos.close();
    }

    /**
     * append video
     * @param videos
     * @param dst
     * @throws IOException
     */
    private void MergeVideo(String[] videos,String dst) throws IOException{
        Movie[] inMovies = new Movie[videos.length];
        int index = 0;
        for(String video:videos)
        {
            inMovies[index] = MovieCreator.build(video);
            index++;
        }
        List<Track> videoTracks = new LinkedList<Track>();
        List<Track> audioTracks = new LinkedList<Track>();
        for (Movie m : inMovies) {
            for (Track t : m.getTracks()) {
                if (t.getHandler().equals("soun")) {
                    audioTracks.add(t);
                }
                if (t.getHandler().equals("vide")) {
                    videoTracks.add(t);
                }
            }
        }

        Movie result = new Movie();

        if (audioTracks.size() > 0) {
            result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
        }
        if (videoTracks.size() > 0) {
            result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
        }
        Container out = new DefaultMp4Builder().build(result);
        FileChannel fc = new RandomAccessFile(String.format(dst), "rw").getChannel();
        out.writeContainer(fc);
        fc.close();
    }

    /**
     *
     * @param track
     * @return
     */
    protected  long getDuration(Track track) {
        long duration = 0;
        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
            duration += entry.getCount() * entry.getDelta();
        }
        return duration;
    }

    /**
     *
     * @param track
     * @param cutHere
     * @param next
     * @return
     */
    private  double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }

    /**
     * print work
     * @param track
     */
    private  void printTime(Track track) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
    }


    public String getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(String videoFile) {
        this.videoFile = videoFile;
    }

    public Double getVideoFPS() {
        return videoFPS;
    }

    public void setVideoFPS(Double videoFPS) {
        this.videoFPS = videoFPS;
    }

    public Integer getCutCount() {
        return cutCount;
    }

    public void setCutCount(Integer cutCount) {
        this.cutCount = cutCount;
    }

    public Long getVideoTimeLength() {
        return videoTimeLength;
    }

    public void setVideoTimeLength(Long videoTimeLength) {
        this.videoTimeLength = videoTimeLength;
    }

    public Long getPerVideoFrames() {
        return perVideoFrames;
    }

    public void setPerVideoFrames(Long perVideoFrames) {
        this.perVideoFrames = perVideoFrames;
    }

    public Long getTotalFrames() {
        return totalFrames;
    }

    public void setTotalFrames(Long totalFrames) {
        this.totalFrames = totalFrames;
    }

    public List<VideoTimeRange> getVideoTimeRanges() {
        return videoTimeRanges;
    }

    public void setVideoTimeRanges(List<VideoTimeRange> videoTimeRanges) {
        this.videoTimeRanges = videoTimeRanges;
    }

    public Integer getPerVideoSeconds() {
        return perVideoSeconds;
    }

    public void setPerVideoSeconds(Integer perVideoSeconds) {
        this.perVideoSeconds = perVideoSeconds;
    }
}
