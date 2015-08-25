require("task")

task_list = {}

function task_list:add(obj)
    self[#self + 1] = obj
end
function task_list:remove_last()
    self[#self] = nil
end
function task_list:remove(obj)
    local i = nil
    for k,v in ipairs(self) do
        if v == obj then
            i = k
            break;
        end
    end
    if i then
        self[i] = self[#self]
        self[#self] = nil
    end
end

task_stack = {}
task_stack.add = task_list.add
task_stack.remove_last = task_list.remove_last
task_stack.remove = task_list.remove
function task_stack:last()
    if #self == 0 then return nil end
    return self[#self]
end

function run_task_parallel(task, ...)
    local new = setmetatable({}, { __index = task })
    if new.init then new:init(...) end
    task_list:add(new)
end

function run_task(task, ...)
    local new = setmetatable({}, { __index = task })
    if new.init then new:init(...) end
    task_stack:add(new)
end

function packet_handler(...)
    local args = table.pack(...)
    local p = "packet_handler ";
    for _,v in ipairs(args) do
        if type(v) == "boolean" then
            if v then v = "[true]" else v = "[false]" end
        end
        if v == nil then v = "[nil]" end
        if type(v) == "table" then v = "[table]" end
        p = p .. v .. " ";
    end
    print(p)

    for _, task in ipairs(task_list) do
        if task.packet_handler then task:packet_handler(args) end
    end

    local task = task_stack:last()
    if task.packet_handler then task:packet_handler(args) end
end

function loop_body()
    for _, task in ipairs(task_list) do
        if task.tick then task:tick() end
        if task.finished then task_list:remove(task) end
    end

    local task = task_stack:last()
    if task.tick then task:tick() end
    if task.finished then task_stack:remove_last() end

    return true
end

run_task_parallel(task.leader_commands)
run_task_parallel(task.npc_listener)

run_task(task.follow_leader)
