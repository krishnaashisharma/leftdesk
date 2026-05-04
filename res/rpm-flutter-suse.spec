Name:       leftdesk
Version:    1.4.6
Release:    0
Summary:    RPM package
License:    GPL-3.0
URL:        https://github.com/krishnaashisharma/leftdesk
Vendor:     leftdesk <info@leftdesk.app>
Requires:   gtk3 libxcb1 libXfixes3 alsa-utils libXtst6 libva2 pam gstreamer-plugins-base gstreamer-plugin-pipewire
Recommends: libayatana-appindicator3-1 xdotool
Provides:   libdesktop_drop_plugin.so()(64bit), libdesktop_multi_window_plugin.so()(64bit), libfile_selector_linux_plugin.so()(64bit), libflutter_custom_cursor_plugin.so()(64bit), libflutter_linux_gtk.so()(64bit), libscreen_retriever_plugin.so()(64bit), libtray_manager_plugin.so()(64bit), liburl_launcher_linux_plugin.so()(64bit), libwindow_manager_plugin.so()(64bit), libwindow_size_plugin.so()(64bit), libtexture_rgba_renderer_plugin.so()(64bit)

# https://docs.fedoraproject.org/en-US/packaging-guidelines/Scriptlets/

%description
LeftDesk - Remote Desktop Solutions, written in Rust.

%prep
# we have no source, so nothing here

%build
# we have no source, so nothing here

# %global __python %{__python3}

%install

mkdir -p "%{buildroot}/usr/share/leftdesk" && cp -r ${HBB}/flutter/build/linux/x64/release/bundle/* -t "%{buildroot}/usr/share/leftdesk"
mkdir -p "%{buildroot}/usr/bin"
install -Dm 644 $HBB/res/leftdesk.service -t "%{buildroot}/usr/share/leftdesk/files"
install -Dm 644 $HBB/res/leftdesk.desktop -t "%{buildroot}/usr/share/leftdesk/files"
install -Dm 644 $HBB/res/leftdesk-link.desktop -t "%{buildroot}/usr/share/leftdesk/files"
install -Dm 644 $HBB/res/128x128@2x.png "%{buildroot}/usr/share/icons/hicolor/256x256/apps/leftdesk.png"
install -Dm 644 $HBB/res/scalable.svg "%{buildroot}/usr/share/icons/hicolor/scalable/apps/leftdesk.svg"

%files
/usr/share/leftdesk/*
/usr/share/leftdesk/files/leftdesk.service
/usr/share/icons/hicolor/256x256/apps/leftdesk.png
/usr/share/icons/hicolor/scalable/apps/leftdesk.svg
/usr/share/leftdesk/files/leftdesk.desktop
/usr/share/leftdesk/files/leftdesk-link.desktop

%changelog
# let's skip this for now

%pre
case "$1" in
  1)
    # for install
  ;;
  2)
    # for upgrade
    systemctl stop leftdesk || true
  ;;
esac

%post
cp /usr/share/leftdesk/files/leftdesk.service /etc/systemd/system/leftdesk.service
cp /usr/share/leftdesk/files/leftdesk.desktop /usr/share/applications/
cp /usr/share/leftdesk/files/leftdesk-link.desktop /usr/share/applications/
ln -sf /usr/share/leftdesk/leftdesk /usr/bin/leftdesk
systemctl daemon-reload
systemctl enable leftdesk
systemctl start leftdesk
update-desktop-database

%preun
case "$1" in
  0)
    # for uninstall
    systemctl stop leftdesk || true
    systemctl disable leftdesk || true
    rm /etc/systemd/system/leftdesk.service || true
  ;;
  1)
    # for upgrade
  ;;
esac

%postun
case "$1" in
  0)
    # for uninstall
    rm /usr/bin/leftdesk || true
    rmdir /usr/lib/leftdesk || true
    rmdir /usr/local/leftdesk || true
    rmdir /usr/share/leftdesk || true
    rm /usr/share/applications/leftdesk.desktop || true
    rm /usr/share/applications/leftdesk-link.desktop || true
    update-desktop-database
  ;;
  1)
    # for upgrade
    rmdir /usr/lib/leftdesk || true
    rmdir /usr/local/leftdesk || true
  ;;
esac
