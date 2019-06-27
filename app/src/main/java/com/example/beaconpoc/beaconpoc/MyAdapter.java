package com.example.beaconpoc.beaconpoc;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
    private List<BeaconInfo> beaconInfoList;


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        /*public TextView major;
        public TextView uuid;
        public TextView minor;
        public TextView distance;*/
        public TextView name;
        public  TextView timeFound;
        public MyViewHolder(View view) {
            super(view);
         /*   uuid = (TextView) view.findViewById(R.id.uuid);
            major = (TextView) view.findViewById(R.id.major);
            minor = (TextView) view.findViewById(R.id.minor);
            distance = (TextView) view.findViewById(R.id.distance);*/

            name = (TextView) view.findViewById(R.id.name);
            timeFound = (TextView) view.findViewById(R.id.timeFound);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MyAdapter(List<BeaconInfo> l) {
        this.beaconInfoList = l;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MyAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view

        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.my_text_view, parent, false);

        return new MyViewHolder(itemView);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        BeaconInfo beaconInfo = beaconInfoList.get(position);
        holder.name.setText(beaconInfo.getName());
        holder.timeFound.setText(beaconInfo.getTimeFound());

        /*
        holder.uuid.setText("UUID: " + beaconInfo.getUuid());
        holder.major.setText("Major: " + String.valueOf(beaconInfo.getMajor()));
        holder.minor.setText("Minor: " + String.valueOf(beaconInfo.getMinor()));
        holder.distance.setText("Approx. distance: " + String.format("%.2f", beaconInfo.getDistance()) + "m");*/

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return beaconInfoList.size();
    }
}
