package com.crenu.jbus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    public static HashMap<String, ArrayList<String>> timeTable = new HashMap<>();
    public static int nowBus = 0;
    public static ArrayList<String> busList = new ArrayList<>();
    TextView remainTime;
    TextView targetTime;
    TextView busNumber;
    ProgressBar progressBar;
    ListView listView ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        remainTime = (TextView) findViewById(R.id.ramin_time);
        targetTime = (TextView) findViewById(R.id.target_time);
        progressBar = (ProgressBar) findViewById(R.id.record_progress_bar);
        busNumber = (TextView) findViewById(R.id.bus_number);
        listView = findViewById(R.id.bus_list);
        try {
            loadData();
        } catch (IOException | CsvException e) {
            throw new RuntimeException(e);
        }
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                ArrayList<BusInfo> busInfos = new ArrayList<>();
                for(int pos = 0; pos < busList.size(); pos++){
                    String busName = busList.get(pos);
                    ArrayList<String> timeList = timeTable.get(busName);
                    if(timeList == null) return;
                    long diffSec = -1;
                    String time = timeList.get(0);
                    long disTime = 0;
                    for(int i = 0; i < timeList.size() ;i++){
                        time = timeList.get(i);
                        diffSec = getTime(time);
                        if(diffSec < 0){
                            diffSec = -1;
                            time = timeList.get(0);
                            continue;
                        }
                        String preTime = "00:00";
                        if(i - 1 > 0){
                            preTime = timeList.get(i-1);
                        }
                        disTime = getTimeDistance(preTime, time);
                        break;
                    }
                    BusInfo busInfo = new BusInfo(busName, disTime, diffSec, time, pos);
                    busInfos.add(busInfo);
                }
                if(busInfos.size() <= nowBus) return;
                BusInfo busInfo = busInfos.get(nowBus);
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        remainTime.setText(busInfo.getText());
                        busNumber.setText(busInfo.busNumber + "번 버스");
                        if(busInfo.diffSec < 0){
                            targetTime.setText("다음날 첫 차 " + busInfo.targtTime);
                            progressBar.setProgress(100);
                            ListView_Adapter mAdapter = new ListView_Adapter(getBaseContext(), busInfos);
                            listView.setAdapter(mAdapter);
                            return;
                        }
                        targetTime.setText("도착 예정 " + busInfo.targtTime);
                        progressBar.setProgress(100-(int) ((busInfo.diffSec/(double)busInfo.disTime) * 100));
                        ListView_Adapter mAdapter = new ListView_Adapter(getBaseContext(), busInfos);
                        listView.setAdapter(mAdapter);
                    }
                });
            }
        };
        timer.schedule(timerTask, 0, 1000);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                nowBus = position;
            }
        });
    }

    private long getTime(String time){
        Date date = new Date();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int c_hour = calendar.get(Calendar.HOUR_OF_DAY);
        int c_min = calendar.get(Calendar.MINUTE);
        int c_sec = calendar.get(Calendar.SECOND);

        Calendar baseCal = new GregorianCalendar(year,month,day,c_hour,c_min,c_sec);
        String[] spilData = time.split(":");
        Calendar targetCal = new GregorianCalendar(year,month,day,Integer.parseInt(spilData[0]),Integer.parseInt(spilData[1]),0);  //비교대상날짜

        long diffSec = (targetCal.getTimeInMillis() - baseCal.getTimeInMillis()) / 1000;
        if(diffSec < 0 || Math.abs(diffSec) < 30){
            return -1;
        }
        return diffSec;
    }

    private long getTimeDistance(String preTime, String time){
        Date date = new Date();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String[] preSpilData = preTime.split(":");
        Calendar baseCal = new GregorianCalendar(year,month,day,Integer.parseInt(preSpilData[0]),Integer.parseInt(preSpilData[1]),0);
        String[] spilData = time.split(":");
        Calendar targetCal = new GregorianCalendar(year,month,day,Integer.parseInt(spilData[0]),Integer.parseInt(spilData[1]),0);

        return (targetCal.getTimeInMillis() - baseCal.getTimeInMillis()) / 1000;
    }

    private void loadData() throws IOException, CsvException {
        AssetManager assetManager = this.getAssets();
        InputStream inputStream = assetManager.open("timeTable.csv");
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream));

        List<String[]> allContent = (List<String[]>) csvReader.readAll();
        int index = 0;
        for(String content[] : allContent){
            int i = 0;
            for(String time : content){
                if(time == "0:00") continue;
                if(index == 0){
                    busList.add(time);
                    timeTable.put(time, new ArrayList<>());
                }else{
                    Objects.requireNonNull(timeTable.get(busList.get(i))).add(time);
                }
                i++;
            }
            index++;
        }
    }

    private class BusInfo {
        String busNumber;
        long diffSec;
        long disTime;
        String targtTime;
        int index;

        public BusInfo(String busNumber, long disTime, long diffSec, String targtTime, int index){
            this.busNumber = busNumber;
            this.disTime = disTime;
            this.diffSec = diffSec;
            this.targtTime = targtTime;
            this.index = index;
        }

        public String getText(){
            int hourTime = (int)Math.floor((double)(this.diffSec/3600));
            int minTime = (int)Math.floor((double)(((this.diffSec - (3600 * hourTime)) / 60)));
            int secTime = (int)Math.floor((double)(((this.diffSec - (3600 * hourTime)) - (60 * minTime))));

            String hour = String.format("%02d", hourTime);
            String min = String.format("%02d", minTime);
            String sec = String.format("%02d", secTime);
            return hour + ":" + min + ":" + sec;
        }
    }

    private class ListView_Adapter extends BaseAdapter {
        // 보여줄 Item 목록을 저장할 List
        List<BusInfo> items = null;
        Context context;

        // Adapter 생성자 함수
        public ListView_Adapter(Context context, List<BusInfo> items) {
            this.items = items;
            this.context = context;
        }

        // Adapter.getCount(), 아이템 개수 반환 함수
        @Override
        public int getCount() {
            return items.size();
        }

        // Adapter.getItem(int position), 해당 위치 아이템 반환 함수
        @Override
        public BusInfo getItem(int position) {
            return items.get(position);
        }

        // Adapter.getItemId(int position), 해당 위치 반환 함수
        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Infalter 구현 방법 1
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = layoutInflater.inflate(R.layout.list_item, parent, false);

            // ListView의 Item을 구성하는 뷰 연결
            TextView number = view.findViewById(R.id.bus_number);
            TextView raminTime = view.findViewById(R.id.remain_time);

            // ListView의 Item을 구성하는 뷰 세팅
            BusInfo item = items.get(position);
            number.setText(item.busNumber);
            raminTime.setText(item.getText());

            // 설정한 view를 반환해줘야 함
            return view;
        }

    }
}