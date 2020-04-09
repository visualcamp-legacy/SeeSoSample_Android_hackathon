package visual.camp.sample.launcher.device;

import android.graphics.PointF;

public enum GazeDevice {
  // 디바이스의 하드웨어 정보 직접 카메라의 위치와 스크린의 크기를 측정한 정보
  // 기기 모델명 갤럭시s9만 보자면 s9 SM-960*의 형태
  // SM_G960N("SM-G960N", -45f, 3f, 64f, 132f, 1440, 2960), // galaxy s9

  DEFAULT("default", -68f, -5f), // galaxy tab 6 lite
  SM_P615N("SM-P615N", -68f, -5f), // galaxy tab 6 lite
  SM_G960N("SM-G960N", -45f, -3f), // galaxy s9
  SM_G965N("SM-G965N", -50f, -3f), // galaxy s9+
  SM_G950N("SM-G950N", -45f, -3f), // galaxy s8
  SM_G930L("SM-G930L", -55f, -9f), // galaxy s7
  SM_G985N("SM-G985N", -57f, 3f), // galaxy s10
  SM_G977N("SM-G977N", -57f, 3f), // galaxy s10 5g
  SM_J710K("SM-J710K", -17f, -10f), // galaxy j7
  SM_T720("SM-T720", -72f, -4f), // galaxy tab s5e
  SM_T536("SM-T536", -145f, -89f), // galaxy tab4
  LG_F600S("LG-F600S", -12f, -5.5f), // lg v10
  LM_G820N("LM-G820N", -25f, 1f), // lg g8
  PAFM00("PAFM00", -52f, -5.5f), // oppo findx
  PCRM00("PCRM00", -7.f, -4.f), // oppo reno3 pro
  SMT865N("SM-T865N", -71f, -5.f); // galaxy tab s6
  public String model; // 기기 모델명

  /*
  Screen Origin (x1, y1)
  x1, y1: 디바이스의 카메라 원점을 기준으로 스크린의 시작지점의 물리적 좌표
  아래는 카메라가 화면 스크린 위에 존재하는 모바일 폰이 portrait일때

                               ^ +(y axis)
                               |
  -----------------------------|-------------------
  ||                           |                 ||
  ||                           O --------------------> +(x axis)
  ||                     (Camera (0, 0))         ||
  ||                                             ||
  ||-----------------------------------------------
  |o(Screen Origin(x1, y1))------------------------
  ||          Screen                             ||
  ||                                             ||
  카메라 좌표축에서 스크린의 원점 좌표 (x, y)
  카메라가 스크린의 시작점보다 더 오른쪽에 있다면 x1 < 0
  카메라가 스크린의 시작점보다 더 위에 있다면 y1 < 0
   */

  /**
   * 축에 관한 자세한 설명은 위에
   */
  public float screen_origin_x, screen_origin_y; // 카메라 위치 mm

  GazeDevice(String model, float screen_origin_x, float screen_origin_y) {
    this.model = model;
    this.screen_origin_x = screen_origin_x;
    this.screen_origin_y = screen_origin_y;
  }

  public static GazeDevice getDevice(String model_name) {
    for (GazeDevice device : GazeDevice.values()) {
      if (device.model.equals(model_name)) {
        return device;
      }
    }
    // 아무것도 없으면 default
    return DEFAULT;
  }
  public static PointF getDeviceScreenOrigin(String model_name) {
    for (GazeDevice device : GazeDevice.values()) {
      if (device.model.equals(model_name)) {
        return new PointF(device.screen_origin_x, device.screen_origin_y);
      }
    }
    // 아무것도 없으면 default
    return new PointF(DEFAULT.screen_origin_x, DEFAULT.screen_origin_y);
  }

}