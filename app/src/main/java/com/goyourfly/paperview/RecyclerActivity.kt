package com.goyourfly.paperview

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_recycler.*
import kotlinx.android.synthetic.main.item_large.view.*
import kotlinx.android.synthetic.main.item_recycler.view.*

/**
 * Created by gaoyufei on 2017/11/28.
 */

class RecyclerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MyAdapter()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home){
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class MyAdapter : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
        override fun getItemCount(): Int {
            return 100
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recycler, parent, false))
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.itemView.paperView.fold(false, false)
        }

        class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            init {
                view.setOnClickListener {
                    view.paperView.unfold(true, false)
                }

                view.btn_close.setOnClickListener {
                    view.paperView.fold(true, false)
                }
            }
        }
    }
}