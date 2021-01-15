package com.example.iotproject;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;

import java.util.ArrayList;

public class ChartController {
    private LineChart lineChart;
    private static ChartController instance;

    //TODO substitute with values from BT
    private ArrayList<Entry> data = new ArrayList<>();

    private ChartController(){
    }

    public static ChartController getInstance(){
        if(instance==null) instance = new ChartController();
            return instance;
    }

    public void clear(){
        lineChart.clear();
    }

    public void setLineChart(LineChart lineChart) {
        this.lineChart = lineChart;

        LineDataSet set1 = new LineDataSet(data, "Temperatures");
        set1.setFillAlpha(110);

        ArrayList<ILineDataSet> allDataSets = new ArrayList<>();
        allDataSets.add(set1);

        LineData lineData = new LineData(allDataSets);

        lineChart.setData(lineData);
    }

    private void update(){
        // NEED to be called after setting data
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setVisibleXRangeMaximum(6);
        lineChart.getDescription().setEnabled(false);
    }

    public void addEntry(float value) {

        LineData data = lineChart.getData();

        if (data == null) {
            data = new LineData();
            lineChart.setData(data);
        }

        ILineDataSet set = data.getDataSetByIndex(0);

        data.addEntry(new Entry(set.getEntryCount(), value), 0);
        data.notifyDataChanged();

        // let the chart know it's data has changed
        lineChart.notifyDataSetChanged();

        lineChart.moveViewTo(data.getEntryCount() - 7, 50f, YAxis.AxisDependency.LEFT);
        update();
    }
}
