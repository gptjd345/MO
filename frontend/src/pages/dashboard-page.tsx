import { useState, forwardRef, useEffect } from "react";
import { useSearch } from "wouter";
import { useActiveTodos, useCompletedTodos, useCompletedPage, useTodoMutations, type Todo, type SortOption } from "@/hooks/use-todos";
import { useAuth } from "@/hooks/use-auth";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { LayoutShell } from "@/components/layout-shell";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
  DialogDescription
} from "@/components/ui/dialog";
import { Plus, Trash2, Pencil, Calendar as CalendarIcon, Loader2, Star, ArrowRight, CheckCircle2, ArrowUpDown, Search, X, Zap, ChevronLeft, ChevronRight, RotateCcw } from "lucide-react";
import { FREE_TASK_LIMIT } from "@/components/layout-shell";
import { motion, AnimatePresence } from "framer-motion";
import { format } from "date-fns";
import type { DateRange } from "react-day-picker";

const createTodoSchema = z.object({
  title: z.string().min(1, "Title is required"),
  content: z.string().optional(),
});

const editTodoSchema = z.object({
  title: z.string().min(1, "Title is required"),
  content: z.string().optional(),
});

const sortLabels: Record<SortOption, string> = {
  newest: "Latest",
  oldest: "Oldest",
  name: "Name",
  deadline: "Deadline",
  priority: "Priority",
};


const priorityConfig = {
  HIGH: { label: "High", stars: 3, color: "text-red-400", bgClass: "bg-red-500/10 text-red-400 border-red-500/20" },
  MEDIUM: { label: "Medium", stars: 2, color: "text-yellow-400", bgClass: "bg-yellow-500/10 text-yellow-400 border-yellow-500/20" },
  LOW: { label: "Low", stars: 1, color: "text-blue-400", bgClass: "bg-blue-500/10 text-blue-400 border-blue-500/20" },
};

function PriorityStars({ priority }: { priority: "HIGH" | "MEDIUM" | "LOW" }) {
  const config = priorityConfig[priority];
  return (
    <div className="flex items-center gap-0.5" data-testid={`priority-stars-${priority.toLowerCase()}`}>
      {Array.from({ length: 3 }).map((_, i) => (
        <Star
          key={i}
          className={`h-3 w-3 ${i < config.stars ? config.color : "text-muted-foreground/30"}`}
          fill={i < config.stars ? "currentColor" : "none"}
        />
      ))}
    </div>
  );
}

function PriorityBadge({ priority }: { priority: "HIGH" | "MEDIUM" | "LOW" }) {
  const config = priorityConfig[priority];
  return (
    <Badge variant="outline" className={`text-xs ${config.bgClass}`} data-testid={`badge-priority-${priority.toLowerCase()}`}>
      <PriorityStars priority={priority} />
      <span className="ml-1">{config.label}</span>
    </Badge>
  );
}

function DateRangeDisplay({ startDate, endDate, completed }: { startDate: string | null; endDate: string | null; completed: boolean }) {
  if (!startDate && !endDate) return null;

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const endD = endDate ? new Date(endDate + "T00:00:00") : null;
  const isOverdue = endD && endD < today && !completed;

  return (
    <div className={`inline-flex items-center gap-1.5 text-xs ${isOverdue ? "text-red-400" : "text-muted-foreground"}`}
         data-testid="text-date-range">
      <CalendarIcon className="h-3 w-3 shrink-0" />
      {startDate && (
        <span data-testid="text-start-date">{format(new Date(startDate + "T00:00:00"), "MMM d")}</span>
      )}
      {startDate && endDate && (
        <ArrowRight className="h-3 w-3 shrink-0" />
      )}
      {endDate && (
        <span data-testid="text-end-date">{format(new Date(endDate + "T00:00:00"), "MMM d")}</span>
      )}
      {isOverdue && <span className="text-red-400 font-medium">overdue</span>}
    </div>
  );
}

function DateRangePicker({
  dateRange,
  onDateRangeChange,
  triggerTestId,
}: {
  dateRange: DateRange | undefined;
  onDateRangeChange: (range: DateRange | undefined) => void;
  triggerTestId: string;
}) {
  const [open, setOpen] = useState(false);

  const displayText = () => {
    if (!dateRange?.from && !dateRange?.to) return "Pick date range";
    if (dateRange.from && !dateRange.to) return format(dateRange.from, "MMM d, yyyy");
    if (dateRange.from && dateRange.to) {
      return `${format(dateRange.from, "MMM d")} - ${format(dateRange.to, "MMM d")}`;
    }
    return "Pick date range";
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className="w-full justify-start text-left font-normal bg-background/50 border-white/10"
          data-testid={triggerTestId}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {displayText()}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0 bg-card border-white/10" align="start">
        <Calendar
          mode="range"
          selected={dateRange}
          onSelect={onDateRangeChange}
          numberOfMonths={2}
          initialFocus
        />
        <div className="p-2 border-t border-white/10 flex items-center justify-between gap-2">
          <Button
            variant="ghost"
            size="sm"
            className={dateRange?.from || dateRange?.to ? "text-muted-foreground" : "invisible"}
            onClick={() => onDateRangeChange(undefined)}
            data-testid="button-clear-dates"
          >
            Reset
          </Button>
          <Button
            size="sm"
            onClick={() => setOpen(false)}
            data-testid="button-confirm-dates"
          >
            OK
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { createTodo, updateTodo, batchComplete, batchUndo, deleteTodo, isCreating, isUpdating } = useTodoMutations();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null);
  const [createDateRange, setCreateDateRange] = useState<DateRange | undefined>();
  const [createPriority, setCreatePriority] = useState<string>("MEDIUM");
  const [editDateRange, setEditDateRange] = useState<DateRange | undefined>();
  const [editPriority, setEditPriority] = useState<string>("MEDIUM");
  const [selectedActiveIds, setSelectedActiveIds] = useState<Set<number>>(new Set());
  const [selectedCompletedIds, setSelectedCompletedIds] = useState<Set<number>>(new Set());
  const [sortBy, setSortBy] = useState<SortOption>("newest");
  const [searchInput, setSearchInput] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [activePage, setActivePage] = useState(0);
  const [completedPage, setCompletedPage] = useState(0);
  const [highlightDate, setHighlightDate] = useState<string | null>(null);

  const search = useSearch();
  const completedDateParam = new URLSearchParams(search).get("completedDate");

  const { data: completedPageData } = useCompletedPage(completedDateParam);

  const { data: activeData, isLoading: isActiveLoading } = useActiveTodos(activePage, sortBy, searchQuery);
  const { data: completedData, isLoading: isCompletedLoading } = useCompletedTodos(completedPage, sortBy, searchQuery);

  const incompleteTodos = activeData?.content ?? [];
  const completedTodos = completedData?.content ?? [];
  const isLoading = isActiveLoading && isCompletedLoading;

  const createForm = useForm<z.infer<typeof createTodoSchema>>({
    resolver: zodResolver(createTodoSchema),
    defaultValues: { title: "", content: "" },
  });

  const editForm = useForm<z.infer<typeof editTodoSchema>>({
    resolver: zodResolver(editTodoSchema),
    defaultValues: { title: "", content: "" },
  });

  const onCreateSubmit = (data: z.infer<typeof createTodoSchema>) => {
    createTodo({
      ...data,
      startDate: createDateRange?.from ? format(createDateRange.from, "yyyy-MM-dd") : undefined,
      endDate: createDateRange?.to ? format(createDateRange.to, "yyyy-MM-dd") : undefined,
      priority: createPriority,
    }, {
      onSuccess: () => {
        setIsCreateOpen(false);
        createForm.reset();
        setCreateDateRange(undefined);
        setCreatePriority("MEDIUM");
      },
    });
  };

  const openEditDialog = (todo: Todo) => {
    setEditingTodo(todo);
    editForm.reset({ title: todo.title, content: todo.content || "" });
    const from = todo.startDate ? new Date(todo.startDate + "T00:00:00") : undefined;
    const to = todo.endDate ? new Date(todo.endDate + "T00:00:00") : undefined;
    setEditDateRange(from || to ? { from, to } : undefined);
    setEditPriority(todo.priority);
  };

  const onEditSubmit = (data: z.infer<typeof editTodoSchema>) => {
    if (!editingTodo) return;
    updateTodo({
      id: editingTodo.id,
      ...data,
      startDate: editDateRange?.from ? format(editDateRange.from, "yyyy-MM-dd") : "",
      endDate: editDateRange?.to ? format(editDateRange.to, "yyyy-MM-dd") : "",
      priority: editPriority,
    }, {
      onSuccess: () => {
        setEditingTodo(null);
        editForm.reset();
      },
    });
  };

  const toggleActiveSelect = (id: number) => {
    setSelectedCompletedIds(new Set());
    setSelectedActiveIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleCompletedSelect = (id: number) => {
    setSelectedActiveIds(new Set());
    setSelectedCompletedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleCompleteSelected = () => {
    const ids = Array.from(selectedActiveIds);
    if (ids.length > 0) batchComplete(ids);
    setSelectedActiveIds(new Set());
  };

  const handleUndoSelected = () => {
    const ids = Array.from(selectedCompletedIds);
    if (ids.length > 0) batchUndo(ids);
    setSelectedCompletedIds(new Set());
  };

  useEffect(() => {
    if (!completedDateParam) return;
    setHighlightDate(completedDateParam);
    if (completedPageData !== undefined) {
      setCompletedPage(completedPageData.page);
    }
  }, [completedDateParam, completedPageData]);

  const isPro = user?.plan === "PRO";
  const totalTodos = (activeData?.totalCount ?? 0) + (completedData?.totalCount ?? 0);
  const isAtTaskLimit = !isPro && totalTodos >= FREE_TASK_LIMIT;

  return (
    <LayoutShell>
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold font-display text-white" data-testid="text-page-title">My Tasks</h1>
          <p className="text-muted-foreground mt-1">
            {format(new Date(), "EEEE, MMMM do, yyyy")}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { setSearchQuery(searchInput); setActivePage(0); setCompletedPage(0); } }}
              placeholder="Search..."
              className="pl-8 pr-8 w-[160px] bg-background/50 border-white/10"
              data-testid="input-search"
            />
            {searchInput && (
              <button
                onClick={() => { setSearchInput(""); setSearchQuery(""); setActivePage(0); setCompletedPage(0); }}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground"
                data-testid="button-clear-search"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
          <Select value={sortBy} onValueChange={(v) => { setSortBy(v as SortOption); setActivePage(0); setCompletedPage(0); }}>
            <SelectTrigger className="w-[140px] bg-background/50 border-white/10" data-testid="select-sort">
              <ArrowUpDown className="mr-2 h-3.5 w-3.5 text-muted-foreground" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent className="bg-card border-white/10">
              {(Object.keys(sortLabels) as SortOption[]).map((key) => (
                <SelectItem key={key} value={key} data-testid={`option-sort-${key}`}>
                  {sortLabels[key]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

        <Dialog open={isCreateOpen} onOpenChange={(open) => {
          if (open && isAtTaskLimit) return;
          setIsCreateOpen(open);
        }}>
          <DialogTrigger asChild>
            <Button
              data-testid="button-new-task"
              disabled={isAtTaskLimit}
              className={isAtTaskLimit ? "opacity-60" : ""}
              title={isAtTaskLimit ? `무료 플랜은 최대 ${FREE_TASK_LIMIT}개까지 생성 가능합니다` : undefined}
            >
              <Plus className="mr-2 h-4 w-4" />
              New Task
            </Button>
          </DialogTrigger>
          <DialogContent className="bg-card border-white/10 sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>Create new task</DialogTitle>
              <DialogDescription>Add a new item to your todo list.</DialogDescription>
            </DialogHeader>
            <form onSubmit={createForm.handleSubmit(onCreateSubmit)} className="space-y-4 mt-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Title</label>
                <Input
                  {...createForm.register("title")}
                  placeholder="e.g. Finish project report"
                  className="bg-background/50 border-white/10"
                  data-testid="input-todo-title"
                />
                {createForm.formState.errors.title && (
                  <p className="text-xs text-destructive">{createForm.formState.errors.title.message}</p>
                )}
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Description (Optional)</label>
                <Input
                  {...createForm.register("content")}
                  placeholder="Any details..."
                  className="bg-background/50 border-white/10"
                  data-testid="input-todo-content"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Period</label>
                  <DateRangePicker
                    dateRange={createDateRange}
                    onDateRangeChange={setCreateDateRange}
                    triggerTestId="button-create-date-range"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Priority</label>
                  <Select value={createPriority} onValueChange={setCreatePriority}>
                    <SelectTrigger className="bg-background/50 border-white/10" data-testid="select-create-priority">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="bg-card border-white/10">
                      <SelectItem value="HIGH" data-testid="option-priority-high">
                        <span className="flex items-center gap-2"><PriorityStars priority="HIGH" /> High</span>
                      </SelectItem>
                      <SelectItem value="MEDIUM" data-testid="option-priority-medium">
                        <span className="flex items-center gap-2"><PriorityStars priority="MEDIUM" /> Medium</span>
                      </SelectItem>
                      <SelectItem value="LOW" data-testid="option-priority-low">
                        <span className="flex items-center gap-2"><PriorityStars priority="LOW" /> Low</span>
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <DialogFooter className="pt-4">
                <Button
                  type="submit"
                  className="w-full"
                  disabled={isCreating}
                  data-testid="button-create-task"
                >
                  {isCreating ? <Loader2 className="h-4 w-4 animate-spin" /> : "Create Task"}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
        </div>
      </div>

      <Dialog open={!!editingTodo} onOpenChange={(open) => { if (!open) setEditingTodo(null); }}>
        <DialogContent className="bg-card border-white/10 sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit task</DialogTitle>
            <DialogDescription>Modify your task details.</DialogDescription>
          </DialogHeader>
          <form onSubmit={editForm.handleSubmit(onEditSubmit)} className="space-y-4 mt-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Title</label>
              <Input
                {...editForm.register("title")}
                className="bg-background/50 border-white/10"
                data-testid="input-edit-title"
              />
              {editForm.formState.errors.title && (
                <p className="text-xs text-destructive">{editForm.formState.errors.title.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Description</label>
              <Input
                {...editForm.register("content")}
                className="bg-background/50 border-white/10"
                data-testid="input-edit-content"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Period</label>
                <DateRangePicker
                  dateRange={editDateRange}
                  onDateRangeChange={setEditDateRange}
                  triggerTestId="button-edit-date-range"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Priority</label>
                <Select value={editPriority} onValueChange={setEditPriority}>
                  <SelectTrigger className="bg-background/50 border-white/10" data-testid="select-edit-priority">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-card border-white/10">
                    <SelectItem value="HIGH">
                      <span className="flex items-center gap-2"><PriorityStars priority="HIGH" /> High</span>
                    </SelectItem>
                    <SelectItem value="MEDIUM">
                      <span className="flex items-center gap-2"><PriorityStars priority="MEDIUM" /> Medium</span>
                    </SelectItem>
                    <SelectItem value="LOW">
                      <span className="flex items-center gap-2"><PriorityStars priority="LOW" /> Low</span>
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter className="pt-4">
              <Button
                type="submit"
                className="w-full"
                disabled={isUpdating}
                data-testid="button-save-edit"
              >
                {isUpdating ? <Loader2 className="h-4 w-4 animate-spin" /> : "Save Changes"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {isAtTaskLimit && !isLoading && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center gap-3 p-4 mb-6 rounded-xl bg-yellow-500/10 border border-yellow-500/20"
          data-testid="banner-task-limit"
        >
          <Zap className="h-5 w-5 text-yellow-400 shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-medium text-yellow-400">무료 플랜 일정 한도 도달</p>
            <p className="text-xs text-muted-foreground mt-0.5">현재 {totalTodos}/{FREE_TASK_LIMIT}개 사용 중. 더 많은 일정을 만들려면 Pro 플랜으로 업그레이드하세요.</p>
          </div>
        </motion.div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 text-primary animate-spin" />
        </div>
      ) : (
        <div className="space-y-6">
          {totalTodos === 0 && !isLoading ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="flex flex-col items-center justify-center p-12 text-center border border-dashed border-white/10 rounded-2xl bg-card/20"
            >
              <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center mb-4">
                <CalendarIcon className="h-8 w-8 text-primary" />
              </div>
              <h3 className="text-xl font-semibold text-white">No tasks yet</h3>
              <p className="text-muted-foreground mt-2 max-w-sm">
                You're all caught up! Click the "New Task" button to add something to your list.
              </p>
            </motion.div>
          ) : totalTodos === 0 && searchQuery ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="flex flex-col items-center justify-center p-12 text-center border border-dashed border-white/10 rounded-2xl bg-card/20"
              data-testid="text-no-search-results"
            >
              <div className="w-16 h-16 rounded-full bg-muted/20 flex items-center justify-center mb-4">
                <Search className="h-8 w-8 text-muted-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-white">No results found</h3>
              <p className="text-muted-foreground mt-2 max-w-sm">
                "{searchQuery}" with no matching tasks. Try a different keyword.
              </p>
            </motion.div>
          ) : (
            <>
              {(incompleteTodos.length > 0 || isActiveLoading) && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-medium text-muted-foreground uppercase tracking-wider" data-testid="text-section-active">
                      Active ({activeData?.totalCount ?? 0})
                    </h2>
                    {selectedActiveIds.size > 0 && (
                      <Button size="sm" onClick={handleCompleteSelected} data-testid="button-complete-selected">
                        <CheckCircle2 className="mr-1.5 h-4 w-4" />
                        Complete ({selectedActiveIds.size})
                      </Button>
                    )}
                  </div>
                  <div className="grid gap-3 min-h-[200px]">
                    <AnimatePresence mode="popLayout">
                      {incompleteTodos.map((todo) => (
                        <TodoCard
                          key={todo.id}
                          todo={todo}
                          selected={selectedActiveIds.has(todo.id)}
                          onSelect={toggleActiveSelect}
                          onEdit={openEditDialog}
                          onDelete={(id) => deleteTodo(id)}
                        />
                      ))}
                    </AnimatePresence>
                  </div>
                  {(activeData?.totalPages ?? 0) > 1 && (
                    <div className="flex items-center justify-center gap-2 pt-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7" disabled={activePage === 0} onClick={() => setActivePage(p => p - 1)}>
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-xs text-muted-foreground">{activePage + 1} / {activeData?.totalPages}</span>
                      <Button variant="ghost" size="icon" className="h-7 w-7" disabled={activePage + 1 === activeData?.totalPages} onClick={() => setActivePage(p => p + 1)}>
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </div>
              )}

              {(completedTodos.length > 0 || isCompletedLoading) && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-medium text-muted-foreground uppercase tracking-wider" data-testid="text-section-completed">
                      Completed ({completedData?.totalCount ?? 0})
                    </h2>
                    {selectedCompletedIds.size > 0 && (
                      <Button
                        size="sm"
                        onClick={handleUndoSelected}
                        className="bg-amber-500/20 text-amber-300 border border-amber-500/40 hover:bg-amber-500/35"
                        data-testid="button-undo-selected"
                      >
                        <RotateCcw className="mr-1.5 h-4 w-4" />
                        Undo ({selectedCompletedIds.size})
                      </Button>
                    )}
                  </div>
                  <div className="grid gap-3 min-h-[200px]">
                    <AnimatePresence mode="popLayout">
                      {completedTodos.map((todo) => (
                        <TodoCard
                          key={todo.id}
                          todo={todo}
                          selected={selectedCompletedIds.has(todo.id)}
                          onSelect={toggleCompletedSelect}
                          highlight={highlightDate !== null && todo.completedAt === highlightDate}
                        />
                      ))}
                    </AnimatePresence>
                  </div>
                  {(completedData?.totalPages ?? 0) > 1 && (
                    <div className="flex items-center justify-center gap-2 pt-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7" disabled={completedPage === 0} onClick={() => setCompletedPage(p => p - 1)}>
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-xs text-muted-foreground">{completedPage + 1} / {completedData?.totalPages}</span>
                      <Button variant="ghost" size="icon" className="h-7 w-7" disabled={completedPage + 1 === completedData?.totalPages} onClick={() => setCompletedPage(p => p + 1)}>
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      )}
    </LayoutShell>
  );
}

const TodoCard = forwardRef<HTMLDivElement, {
  todo: Todo;
  selected: boolean;
  onSelect: (id: number) => void;
  onEdit?: (todo: Todo) => void;
  onDelete?: (id: number) => void;
  highlight?: boolean;
}>(function TodoCard({ todo, selected, onSelect, onEdit, onDelete, highlight }, ref) {
  return (
    <motion.div
      ref={ref}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      data-testid={`card-todo-${todo.id}`}
    >
      <Card className={`
        border shadow-sm transition-all duration-200
        ${highlight ? 'border-yellow-400/60 bg-yellow-500/10' : selected ? 'border-primary/40 bg-primary/5' : 'border-white/5'}
        ${todo.completed && !highlight ? 'bg-card/40 opacity-60' : !highlight ? 'bg-card' : ''}
      `}>
        <CardContent className="p-4 flex items-start gap-4">
          <div className="pt-1">
            <Checkbox
              checked={selected}
              onCheckedChange={() => onSelect(todo.id)}
              className="h-5 w-5 border-2 border-white/20 data-[state=checked]:bg-primary data-[state=checked]:border-primary"
              data-testid={`checkbox-todo-${todo.id}`}
            />
          </div>

          <div className="flex-1 min-w-0 space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className={`font-medium text-base ${todo.completed ? 'line-through text-muted-foreground' : 'text-white'}`}
                  data-testid={`text-todo-title-${todo.id}`}>
                {todo.title}
              </h3>
              <PriorityBadge priority={todo.priority} />
            </div>
            {todo.content && (
              <p className={`text-sm truncate ${todo.completed ? 'line-through text-muted-foreground/60' : 'text-muted-foreground'}`}
                 data-testid={`text-todo-content-${todo.id}`}>
                {todo.content}
              </p>
            )}
            {(todo.startDate || todo.endDate) && (
              <DateRangeDisplay startDate={todo.startDate} endDate={todo.endDate} completed={todo.completed} />
            )}
          </div>

          {onEdit && (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => onEdit(todo)}
              data-testid={`button-edit-todo-${todo.id}`}
            >
              <Pencil className="h-4 w-4" />
            </Button>
          )}
          {onDelete && (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => onDelete(todo.id)}
              className="text-muted-foreground hover:text-destructive"
              data-testid={`button-delete-todo-${todo.id}`}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
});
