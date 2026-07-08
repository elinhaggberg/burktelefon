/*
 *  Copyright (C) 2004-2026 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package cx.ring.burktelefon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cx.ring.R
import cx.ring.databinding.ItemBurkContactBinding
import cx.ring.views.AvatarFactory
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import net.jami.smartlist.ConversationItemViewModel

/**
 * Real Jami contact photos + names, imported from the account's conversation
 * list — with an optional local nickname override (e.g. "Mamma" instead of
 * the contact's real Jami name).
 *
 * Renaming is a deliberate two-step gesture: long-pressing a contact "arms"
 * a small pencil badge on it (only one contact armed at a time), and a
 * second, separate tap on that badge opens the rename dialog. A single
 * accidental long-press — an easy thing for a kid to do without meaning
 * to — can't open editing UI on its own.
 */
class BurkContactAdapter(
    private val uiScheduler: Scheduler,
    private val nicknames: BurkNicknames,
    private val onContactClicked: (ConversationItemViewModel) -> Unit,
    private val onRenameRequested: (ConversationItemViewModel) -> Unit
) : RecyclerView.Adapter<BurkContactAdapter.ViewHolder>() {

    private var items: List<ConversationItemViewModel> = emptyList()
    private var armedPosition: Int = RecyclerView.NO_POSITION

    fun submitList(newItems: List<ConversationItemViewModel>) {
        items = newItems
        armedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    /** Call after a nickname changes so the visible list picks it up immediately. */
    fun refreshNicknames() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBurkContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val vm = items[position]
        holder.bind(
            vm, uiScheduler, nicknames,
            isArmed = position == armedPosition,
            onClick = {
                if (position == armedPosition) disarm() else onContactClicked(vm)
            },
            onLongClick = { arm(position) },
            onEditBadgeClick = {
                disarm()
                onRenameRequested(vm)
            }
        )
    }

    private fun arm(position: Int) {
        if (position == armedPosition) return
        val previous = armedPosition
        armedPosition = position
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    private fun disarm() {
        if (armedPosition == RecyclerView.NO_POSITION) return
        val previous = armedPosition
        armedPosition = RecyclerView.NO_POSITION
        notifyItemChanged(previous)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.avatarDisposable?.dispose()
        holder.avatarDisposable = null
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemBurkContactBinding) : RecyclerView.ViewHolder(binding.root) {
        var avatarDisposable: Disposable? = null

        fun bind(
            vm: ConversationItemViewModel,
            uiScheduler: Scheduler,
            nicknames: BurkNicknames,
            isArmed: Boolean,
            onClick: () -> Unit,
            onLongClick: () -> Unit,
            onEditBadgeClick: () -> Unit
        ) {
            val contactUri = vm.getContact()?.contact?.uri?.rawUriString
            val displayName = contactUri?.let { nicknames.resolve(it, vm.title) } ?: vm.title
            binding.burkContactName.text = displayName
            binding.root.contentDescription = binding.root.context.getString(R.string.burk_cd_call_contact, displayName)
            binding.root.setOnClickListener { onClick() }
            binding.root.setOnLongClickListener { onLongClick(); true }
            binding.burkEditBadge.visibility = if (isArmed) View.VISIBLE else View.GONE
            binding.burkEditBadge.setOnClickListener { onEditBadgeClick() }
            avatarDisposable?.dispose()
            avatarDisposable = AvatarFactory.getAvatar(binding.root.context, vm)
                .observeOn(uiScheduler)
                .subscribe { drawable -> binding.burkContactPhoto.setImageDrawable(drawable) }
        }
    }
}
