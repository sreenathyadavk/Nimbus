package com.localcloud.photos.security;

import com.localcloud.photos.model.Device;
import com.localcloud.photos.repository.DeviceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Date;
import java.util.Optional;

@Component
public class DeviceAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private DeviceRepository deviceRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String deviceId = request.getHeader("X-Device-Id");

        if (deviceId == null || deviceId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing X-Device-Id header");
            return false;
        }

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);

        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            if (device.isBlocked()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Device is blocked");
                return false;
            }
            device.setLastSeen(new Date());
            deviceRepository.save(device);
        } else {
            // Auto-register new device
            Device newDevice = new Device(deviceId, new Date(), new Date(), false);
            deviceRepository.save(newDevice);
        }

        // Pass device ID to controller via request attribute
        request.setAttribute("deviceId", deviceId);

        return true;
    }
}
