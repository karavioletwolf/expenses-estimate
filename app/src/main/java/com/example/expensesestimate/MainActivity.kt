package com.example.expensesestimate

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ExpenseItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var amount: Double,
    var startDate: LocalDate,
    var isRecurring: Boolean
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("amount", amount)
        obj.put("startDate", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
        obj.put("isRecurring", isRecurring)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ExpenseItem {
            return ExpenseItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                amount = obj.getDouble("amount"),
                startDate = LocalDate.parse(obj.getString("startDate"), DateTimeFormatter.ISO_LOCAL_DATE),
                isRecurring = obj.getBoolean("isRecurring")
            )
        }
    }
}

class MainActivity : AppCompatActivity() {

    private val expenseItems = mutableListOf<ExpenseItem>()
    private var displayedYearMonth: YearMonth = YearMonth.now()
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("MMMM d")

    private lateinit var tvCurrentMonth: TextView
    private lateinit var tvTotalTally: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var rvCalendarGrid: RecyclerView
    private lateinit var rvMonthExpenses: RecyclerView

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var expenseListAdapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadExpensesFromStorage()

        tvCurrentMonth = findViewById(R.id.tvCurrentMonth)
        tvTotalTally = findViewById(R.id.tvTotalTally)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        rvCalendarGrid = findViewById(R.id.rvCalendarGrid)
        rvMonthExpenses = findViewById(R.id.rvMonthExpenses)

        // 7 columns for Sunday through Saturday
        rvCalendarGrid.layoutManager = GridLayoutManager(this, 7)
        calendarAdapter = CalendarAdapter { selectedDate ->
            onDayClicked(selectedDate)
        }
        rvCalendarGrid.adapter = calendarAdapter

        // Bottom list for all bills this month
        rvMonthExpenses.layoutManager = LinearLayoutManager(this)
        expenseListAdapter = ExpenseAdapter(
            onEdit = { expense -> showAddEditDialog(expense, expense.startDate) },
            onDelete = { expense -> deleteExpense(expense) }
        )
        rvMonthExpenses.adapter = expenseListAdapter

        findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            displayedYearMonth = displayedYearMonth.minusMonths(1)
            refreshAll()
        }

        findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            displayedYearMonth = displayedYearMonth.plusMonths(1)
            refreshAll()
        }

        findViewById<FloatingActionButton>(R.id.fabAddExpense).setOnClickListener {
            showAddEditDialog(null, displayedYearMonth.atDay(1))
        }

        refreshAll()
    }

    private fun getExpensesForMonth(ym: YearMonth): List<ExpenseItem> {
        return expenseItems.filter { item ->
            val itemYm = YearMonth.from(item.startDate)
            if (item.isRecurring) {
                !ym.isBefore(itemYm)
            } else {
                ym == itemYm
            }
        }.sortedBy { item ->
            minOf(item.startDate.dayOfMonth, ym.lengthOfMonth())
        }
    }

    private fun getExpensesForDay(date: LocalDate): List<ExpenseItem> {
        val ym = YearMonth.from(date)
        return expenseItems.filter { item ->
            val itemYm = YearMonth.from(item.startDate)
            if (item.isRecurring) {
                val effectiveDay = minOf(item.startDate.dayOfMonth, ym.lengthOfMonth())
                !ym.isBefore(itemYm) && effectiveDay == date.dayOfMonth
            } else {
                item.startDate == date
            }
        }
    }

    private fun refreshAll() {
        tvCurrentMonth.text = displayedYearMonth.format(monthTitleFormatter)

        val monthList = getExpensesForMonth(displayedYearMonth)
        val total = monthList.sumOf { it.amount }
        tvTotalTally.text = String.format("$%.2f", total)

        if (monthList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvMonthExpenses.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvMonthExpenses.visibility = View.VISIBLE
        }
        expenseListAdapter.submitList(monthList, displayedYearMonth)

        // Generate Calendar Grid items (empty lead days + valid dates)
        val firstDay = displayedYearMonth.atDay(1)
        val daysInMonth = displayedYearMonth.lengthOfMonth()
        val dayOfWeekIndex = firstDay.dayOfWeek.value % 7 // Sun=0, Mon=1, etc.

        val cellDates = mutableListOf<LocalDate?>()
        for (i in 0 until dayOfWeekIndex) {
            cellDates.add(null)
        }
        for (d in 1..daysInMonth) {
            cellDates.add(displayedYearMonth.atDay(d))
        }

        val dayTotalsMap = cellDates.filterNotNull().associateWith { date ->
            getExpensesForDay(date).sumOf { it.amount }
        }

        calendarAdapter.submitData(cellDates, dayTotalsMap)
    }

    private fun onDayClicked(date: LocalDate) {
        val dayExpenses = getExpensesForDay(date)
        if (dayExpenses.isEmpty()) {
            showAddEditDialog(null, date)
        } else {
            val options = dayExpenses.map { "${it.name}: $${String.format("%.2f", it.amount)}" }.toMutableList()
            options.add("+ Add Another Expense")

            AlertDialog.Builder(this)
                .setTitle("Expenses for ${date.format(dayHeaderFormatter)}")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which == options.size - 1) {
                        showAddEditDialog(null, date)
                    } else {
                        val selectedExpense = dayExpenses[which]
                        showItemActionDialog(selectedExpense)
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showItemActionDialog(expense: ExpenseItem) {
        val recurText = if (expense.isRecurring) "Monthly Recurring" else "One-Time"
        AlertDialog.Builder(this)
            .setTitle(expense.name)
            .setMessage("Amount: $${String.format("%.2f", expense.amount)}\nType: $recurText")
            .setPositiveButton("Edit") { _, _ ->
                showAddEditDialog(expense, expense.startDate)
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteExpense(expense)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showAddEditDialog(existingItem: ExpenseItem?, defaultDate: LocalDate) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_expense, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etName = dialogView.findViewById<EditText>(R.id.etExpenseName)
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        val btnPickDate = dialogView.findViewById<Button>(R.id.btnPickDate)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbIsRecurring)

        var selectedDate = existingItem?.startDate ?: defaultDate

        val updateButtonLabel = {
            btnPickDate.text = "Due Date: ${selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        }

        if (existingItem != null) {
            tvTitle.text = "Edit Expense"
            etName.setText(existingItem.name)
            etAmount.setText(existingItem.amount.toString())
            cbRecurring.isChecked = existingItem.isRecurring
        } else {
            tvTitle.text = "Add Expense / Bill"
            cbRecurring.isChecked = false
        }
        updateButtonLabel()

        btnPickDate.setOnClickListener {
            val picker = DatePickerDialog(
                this,
                { _, year, monthOfYear, dayOfMonth ->
                    selectedDate = LocalDate.of(year, monthOfYear + 1, dayOfMonth)
                    updateButtonLabel()
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            )
            picker.show()
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isNotEmpty() && amount > 0.0) {
                    if (existingItem != null) {
                        existingItem.name = name
                        existingItem.amount = amount
                        existingItem.startDate = selectedDate
                        existingItem.isRecurring = cbRecurring.isChecked
                    } else {
                        expenseItems.add(
                            ExpenseItem(
                                name = name,
                                amount = amount,
                                startDate = selectedDate,
                                isRecurring = cbRecurring.isChecked
                            )
                        )
                    }
                    saveExpensesToStorage()
                    refreshAll()
                } else {
                    Toast.makeText(this, "Please enter a valid name and amount.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpense(expense: ExpenseItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to remove '${expense.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                expenseItems.remove(expense)
                saveExpensesToStorage()
                refreshAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveExpensesToStorage() {
        val sharedPref = getSharedPreferences("ExpenseEstimatorPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        expenseItems.forEach { jsonArray.put(it.toJsonObject()) }
        sharedPref.edit().putString("expense_items_json", jsonArray.toString()).apply()
    }

    private fun loadExpensesFromStorage() {
        expenseItems.clear()
        val sharedPref = getSharedPreferences("ExpenseEstimatorPrefs", Context.MODE_PRIVATE)
        val rawJson = sharedPref.getString("expense_items_json", null) ?: return
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                expenseItems.add(ExpenseItem.fromJsonObject(jsonArray.getJSONObject(i)))
            }
        } catch (_: Exception) {}
    }
}

class CalendarAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private val dates = mutableListOf<LocalDate?>()
    private var totalsMap = mapOf<LocalDate, Double>()
    private val today: LocalDate = LocalDate.now()

    fun submitData(newDates: List<LocalDate?>, dayTotals: Map<LocalDate, Double>) {
        dates.clear()
        dates.addAll(newDates)
        totalsMap = dayTotals
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = dates[position]
        holder.bind(date, totalsMap[date] ?: 0.0, today, onDayClick)
    }

    override fun getItemCount(): Int = dates.size

    class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.cardDayCell)
        private val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        private val tvDayAmount: TextView = itemView.findViewById(R.id.tvDayAmount)
        private val dotIndicator: View = itemView.findViewById(R.id.dotIndicator)

        fun bind(date: LocalDate?, total: Double, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
            if (date == null) {
                card.visibility = View.INVISIBLE
                card.setOnClickListener(null)
                return
            }

            card.visibility = View.VISIBLE
            tvDayNumber.text = date.dayOfMonth.toString()

            if (date == today) {
                card.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
                tvDayNumber.setTextColor(Color.parseColor("#1565C0"))
            } else {
                card.setCardBackgroundColor(Color.WHITE)
                tvDayNumber.setTextColor(Color.parseColor("#212121"))
            }

            if (total > 0.0) {
                tvDayAmount.visibility = View.VISIBLE
                tvDayAmount.text = "$${total.toInt()}"
                dotIndicator.visibility = View.VISIBLE
            } else {
                tvDayAmount.visibility = View.GONE
                dotIndicator.visibility = View.GONE
            }

            card.setOnClickListener { onDayClick(date) }
        }
    }
}

class ExpenseAdapter(
    private val onEdit: (ExpenseItem) -> Unit,
    private val onDelete: (ExpenseItem) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private val items = mutableListOf<ExpenseItem>()
    private var currentYearMonth: YearMonth = YearMonth.now()

    fun submitList(newItems: List<ExpenseItem>, yearMonth: YearMonth) {
        items.clear()
        items.addAll(newItems)
        currentYearMonth = yearMonth
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(items[position], currentYearMonth, onEdit, onDelete)
    }

    override fun getItemCount(): Int = items.size

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvItemDay: TextView = itemView.findViewById(R.id.tvItemDay)
        private val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        private val tvItemRecurringTag: TextView = itemView.findViewById(R.id.tvItemRecurringTag)
        private val tvItemAmount: TextView = itemView.findViewById(R.id.tvItemAmount)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnItemEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnItemDelete)

        fun bind(
            expense: ExpenseItem,
            viewingMonth: YearMonth,
            onEdit: (ExpenseItem) -> Unit,
            onDelete: (ExpenseItem) -> Unit
        ) {
            val maxDays = viewingMonth.lengthOfMonth()
            val effectiveDay = minOf(expense.startDate.dayOfMonth, maxDays)

            tvItemDay.text = effectiveDay.toString()
            tvItemName.text = expense.name
            tvItemAmount.text = String.format("$%.2f", expense.amount)

            if (expense.isRecurring) {
                tvItemRecurringTag.visibility = View.VISIBLE
                tvItemRecurringTag.text = "Recurring Monthly"
            } else {
                tvItemRecurringTag.visibility = View.GONE
            }

            btnEdit.setOnClickListener { onEdit(expense) }
            btnDelete.setOnClickListener { onDelete(expense) }
        }
    }
}
