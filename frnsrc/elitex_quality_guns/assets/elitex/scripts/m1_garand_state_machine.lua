local default = require("tacz_default_state_machine")
local GUN_KICK_TRACK_LINE = default.GUN_KICK_TRACK_LINE

local gun_kick_state = setmetatable({}, { __index = default.gun_kick_state })

function gun_kick_state.transition(this, context, input)
    if input == INPUT_SHOOT then
        local track = context:findIdleTrack(GUN_KICK_TRACK_LINE, false)
        local ammo = context:getAmmoCount()

        if ammo == 1 then
            -- Play shoot_last, fallback to STOP if HOLD causes visual issues
            context:runAnimation("shoot_last", track, true, PLAY_ONCE_STOP, 0)
        elseif ammo > 1 then
            context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
        end
    end
    return nil
end

local M = setmetatable({
    gun_kick_state = gun_kick_state
}, { __index = default })

function M:initialize(context)
    default.initialize(self, context)
end

return M
