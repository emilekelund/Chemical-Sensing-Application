package com.example.resistancereader;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BtDeviceAdapter extends RecyclerView.Adapter<BtDeviceAdapter.ViewHolder> {
    private List<BluetoothDevice> mDeviceList;

    // interface for callbacks when item selected
    public interface IOnItemSelectedCallBack {
        void onItemClicked(int position);
    }
    private IOnItemSelectedCallBack mOnItemSelectedCallback;

    public BtDeviceAdapter(List<BluetoothDevice> deviceList,
                           IOnItemSelectedCallBack onItemSelectedCallBack) {
        super();
        mDeviceList = deviceList;
        mOnItemSelectedCallback = onItemSelectedCallBack;

    }

    @NonNull
    @Override
    public BtDeviceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new item view
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.scan_result_item, parent, false);
        final ViewHolder vh = new ViewHolder(itemView, mOnItemSelectedCallback);
        vh.deviceNameView = itemView.findViewById(R.id.device_name);
        vh.deviceInfoView = itemView.findViewById(R.id.device_info);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout managaer)
    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        BluetoothDevice device = mDeviceList.get(position);
        String name = device.getName();
        vh.deviceNameView.setText(name == null ? "Unknown" : name);
        vh.deviceInfoView.setText(String.format("%s, %s", device.getBluetoothClass(), device.getAddress()));

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        TextView deviceNameView;
        TextView deviceInfoView;

        private IOnItemSelectedCallBack mOnItemSelectedCallback;

        ViewHolder(View itemView, IOnItemSelectedCallBack onItemSelectedCallBack) {
            super(itemView);
            itemView.setOnClickListener(this);
            mOnItemSelectedCallback = onItemSelectedCallBack;
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition(); // gets item (row) position
            mOnItemSelectedCallback.onItemClicked(position);
        }
    }
}
