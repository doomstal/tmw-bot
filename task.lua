task = {}

leader_name = "doomstal"
leader = nil

t = 0

function mysplit(inputstr, sep)
    if sep == nil then
        sep = "%s"
    end
    local t={} ; i=1
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        t[i] = str
        i = i + 1
    end
    return t
end

task.follow_leader = {
    init = function(self)
    end,
    tick = function(self)
        if not leader then
            for id, being in pairs(beings) do
                if being.name == leader_name then
                    leader = being
                    break
                end
            end
--[[
        else
            if t >= 10 and (character.x ~= leader.x or character.y ~= leader.y) and (character.dstx ~= leader.x or character.dsty ~= leader.y) then
                send_packet("walk", leader.x, leader.y)
            end
]]
        end
        t = t + 1
    end,
    packet_handler = function(self, args)
        if not leader then
            if args[1] == "being_name" then
                local id = args[2]
                if beings[id].name == leader_name then
                    leader = beings[id]
                end
            end
        elseif args[1] == "being_remove" then
            if args[2] == leader[id] then
                leader = nil
            end
        elseif args[1] == "whisper" and args[2] == leader_name and leader then
            local cmds = mysplit(args[3])
            if cmds[1] == "print" then
                for k,v in pairs(leader) do
                    print(k, v)
                end
            elseif cmds[1] == "walk" and cmds[2] and cmds[3] then
                send_packet("walk", tonumber(cmds[2]), tonumber(cmds[3]))
            elseif cmds[1] == "come" then
                send_packet("walk", leader.x, leader.y)
            end
        end
    end
}
