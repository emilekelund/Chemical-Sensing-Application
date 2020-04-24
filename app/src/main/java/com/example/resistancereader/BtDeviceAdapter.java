package com.example.resistancereader;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class BtDeviceAdapter extends RecyclerView.Adapter<BtDeviceAdapter.ViewHolder> {

    // interface for callbacks when item selected
    public interface IOnItemSelectedCallBack {
        void onItemClicked(int position);
    }
    private IOnItemSelectedCallBack mOnItemSelectedCallback;



    @NonNull
    @Override
    public BtDeviceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull BtDeviceAdapter.ViewHolder holder, int position) {

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
