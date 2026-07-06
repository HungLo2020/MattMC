local default = require("tacz_default_state_machine")

local STATIC_TRACK_LINE = default.STATIC_TRACK_LINE
local MAIN_TRACK = default.MAIN_TRACK
local GUN_KICK_TRACK_LINE = default.GUN_KICK_TRACK_LINE

local main_track_states = default.main_track_states
local static_track_top = default.static_track_top

local inspect_state = setmetatable({}, {__index = main_track_states.inspect})
local idle_state = setmetatable({}, {__index = main_track_states.idle})

-- 🔥 GUN KICK STATE (FIXED SHOOT SYSTEM)
local gun_kick_state = setmetatable({}, { __index = default.gun_kick_state })

function gun_kick_state.transition(this, context, input)
    if input == INPUT_SHOOT then
        local track = context:findIdleTrack(GUN_KICK_TRACK_LINE, false)

        local ammo = context:getAmmoCount()
        local hasBullet = context:hasBulletInBarrel()
        local totalAmmo = ammo + (hasBullet and 1 or 0)

        if totalAmmo == 1 then
            context:runAnimation("shoot_last", track, true, PLAY_ONCE_STOP, 0)
        elseif totalAmmo > 1 then
            context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
        end
    end

    return nil
end

-- helper
local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

local FIRE_MODE_TRACK = increment(static_track_top)
local SWITCH_MODE_TRACK = increment(static_track_top)

-- 🔥 RELOAD LOGIC (AMMO BASED)
local function runReloadAnimation(context)

    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)

    local ammo = context:getAmmoCount()
    local hasBullet = context:hasBulletInBarrel()
    local totalAmmo = ammo + (hasBullet and 1 or 0)

    if totalAmmo >= 5 then
        context:runAnimation("gun_check", track, false, PLAY_ONCE_STOP, 0.2)
        return
    end

    if totalAmmo == 0 then
        context:runAnimation("reload_empty", track, false, PLAY_ONCE_STOP, 0.2)
    elseif totalAmmo == 1 then
        context:runAnimation("reload_4", track, false, PLAY_ONCE_STOP, 0.2)
    elseif totalAmmo == 2 then
        context:runAnimation("reload_3", track, false, PLAY_ONCE_STOP, 0.2)
    elseif totalAmmo == 3 then
        context:runAnimation("reload_2", track, false, PLAY_ONCE_STOP, 0.2)
    elseif totalAmmo == 4 then
        context:runAnimation("reload_1", track, false, PLAY_ONCE_STOP, 0.2)
    end
end

-- 🔍 INSPECT
local function runInspectAnimation(context)
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)

    if (not context:hasBulletInBarrel() and context:getAmmoCount() <= 0) then
        context:runAnimation("inspect_empty", track, false, PLAY_ONCE_STOP, 0.2)
    else
        context:runAnimation("inspect", track, false, PLAY_ONCE_STOP, 0.2)
    end
end

-- 💤 IDLE
function idle_state.transition(this, context, input)

    if (input == INPUT_RELOAD) then
        runReloadAnimation(context)
        return this.main_track_states.idle
    end

    if (input == INPUT_PUT_AWAY) then
        local put_away_time = context:getPutAwayTime()
        local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)

        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, SWITCH_MODE_TRACK))
        context:runAnimation("put_away", track, false, PLAY_ONCE_HOLD, put_away_time * 0.75)
        context:setAnimationProgress(track, 1, true)
        context:adjustAnimationProgress(track, -put_away_time, false)

        return this.main_track_states.final
    end

    if (input == INPUT_INSPECT) then
        runInspectAnimation(context)
        return inspect_state
    end

    return main_track_states.idle.transition(this, context, input)
end

-- 🔍 INSPECT STATE
function inspect_state.transition(this, context, input)
    if (input == INPUT_FIRE_SELECT) then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))
        return main_track_states.idle
    end
    return main_track_states.inspect.transition(this, context, input)
end

-- 🧠 EXPORT
local M = setmetatable({
    main_track_states = setmetatable({
        idle = idle_state,
        inspect = inspect_state
    }, {__index = main_track_states}),
    gun_kick_state = gun_kick_state,
    FIRE_MODE_TRACK = FIRE_MODE_TRACK,
    SWITCH_MODE_TRACK = SWITCH_MODE_TRACK
}, {__index = default})

function M:initialize(context)
    default.initialize(self, context)
end

function M:states()
    return {
        self.base_track_state,
        self.bolt_caught_states.normal,
        self.main_track_states.start,
        self.gun_kick_state,
        self.movement_track_states.idle,
        self.ADS_states.normal,
        self.slide_states.normal
    }
end

return M