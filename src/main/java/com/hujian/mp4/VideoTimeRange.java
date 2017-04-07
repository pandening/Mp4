package com.hujian.mp4;

import java.io.Serializable;

/**
 * Created by hujian on 2017/4/7.
 */
public class VideoTimeRange implements Serializable {

    private Long start = null;
    private Long end = null;
    private Long distance = null;

    /**
     * the constructor
     * @param start
     * @param end
     */
    public VideoTimeRange(long start,long end){
        this.start = start;
        this.end = end;
        this.distance = this.end - this.start;
    }

    @Override
    public String toString(){
        return "["+start+"->"+end+"]("+distance+")";
    }

    public Long getStart() {
        return start;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public Long getEnd() {
        return end;
    }

    public void setEnd(Long end) {
        this.end = end;
    }

    public Long getDistance() {
        return distance;
    }

    public void setDistance(Long distance) {
        this.distance = distance;
    }
}
