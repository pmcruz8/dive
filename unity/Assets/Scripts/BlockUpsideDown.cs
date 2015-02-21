﻿using UnityEngine;
using System.Collections;

public class BlockUpsideDown : MonoBehaviour {

	public Camera camLeft;
	public Camera camRight;
	public GameObject sphereLeft;
	public GameObject sphereRight;
	private bool orientation;

	// Use this for initialization
	void Start () {
		orientation = (Screen.orientation == ScreenOrientation.LandscapeLeft);
		if (!orientation) {
			OnRotate180 ();
		}
	}
	
	// Update is called once per frame
	void Update () {
		bool newOrientation = (Screen.orientation == ScreenOrientation.LandscapeLeft);
		if (orientation != newOrientation) {
			orientation = newOrientation;
			OnRotate180();
		}
	}

	void OnRotate180() {
		Rect leftRect = camLeft.rect;
		camLeft.rect = camRight.rect;
		camRight.rect = leftRect;
		Vector3 vec = sphereLeft.transform.localScale;
		sphereLeft.transform.localScale = new Vector3 (1f, -vec.y, 1f);
		if (sphereRight != null) {
			sphereRight.transform.localScale = new Vector3 (1f, -vec.y, 1f);
		}
	}
}